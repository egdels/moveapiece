/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/*
 * Windows Runtime backend for WindowsPegasusBleTransport (see that class's
 * Javadoc). Raw bytes only - no DGT message interpretation here, mirroring
 * app/src/main/java/de/schliweb/pegasus/bluetooth/AndroidPegasusBleTransport.java's
 * own phase-1 boundary and desktop/src/main/native/macos/PegasusBleMac.m's
 * equivalent macOS boundary; pegasus-core's PegasusGameBridge-equivalent
 * owns everything above that.
 *
 * Threading: unlike CoreBluetooth (one delegate queue, coinciding with
 * JavaFX's Application Thread on macOS), Windows Runtime async completions
 * and events (BluetoothLEAdvertisementWatcher::Received,
 * BluetoothLEDevice::ConnectionStatusChanged, GattCharacteristic::
 * ValueChanged, and every awaited IAsyncOperation continuation below) can
 * land on *any* WinRT thread-pool thread - there is no single queue to lean
 * on. Every JNI callback below therefore attaches its own thread to the JVM
 * per call (see CurrentEnv()) rather than assuming an already-attached
 * thread the way the macOS bridge does.
 *
 * winrt::init_apartment() is called once, in nativeCreate, on whatever
 * thread constructs the bridge (the JavaFX Application Thread) - WinRT's
 * own thread pool manages apartment state for the threads it resumes
 * coroutines on, so no further init_apartment() calls are needed elsewhere.
 *
 * Async control flow uses C++/WinRT coroutines (co_await / fire_and_forget)
 * rather than nested .Completed() callbacks - the standard, Microsoft-
 * documented pattern for driving Windows Runtime async APIs from a
 * non-coroutine caller (here, a JNI-invoked function).
 */

#include <windows.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Devices.Bluetooth.h>
#include <winrt/Windows.Devices.Bluetooth.Advertisement.h>
#include <winrt/Windows.Devices.Bluetooth.GenericAttributeProfile.h>
#include <winrt/Windows.Storage.Streams.h>
#include <jni.h>
#include <string>
#include <vector>
#include <cwchar>

using namespace winrt;
using namespace winrt::Windows::Devices::Bluetooth;
using namespace winrt::Windows::Devices::Bluetooth::Advertisement;
using namespace winrt::Windows::Devices::Bluetooth::GenericAttributeProfile;
using namespace winrt::Windows::Storage::Streams;

namespace {

std::string ToUtf8(std::wstring_view w) {
    if (w.empty()) {
        return {};
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, w.data(), static_cast<int>(w.size()), nullptr, 0, nullptr, nullptr);
    std::string result(size, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.data(), static_cast<int>(w.size()), result.data(), size, nullptr, nullptr);
    return result;
}

std::wstring JStringToWString(JNIEnv* env, jstring s) {
    const jchar* chars = env->GetStringChars(s, nullptr);
    jsize len = env->GetStringLength(s);
    std::wstring result(reinterpret_cast<const wchar_t*>(chars), len);
    env->ReleaseStringChars(s, chars);
    return result;
}

winrt::guid ParseGuid(std::wstring const& uuid) {
    // Standard 8-4-4-4-12 hex grouping, e.g. "6e400001-b5a3-f393-e0a9-e50e24dcca9e".
    unsigned long data1 = 0;
    unsigned int data2 = 0, data3 = 0;
    unsigned int d[8] = {0, 0, 0, 0, 0, 0, 0, 0};
    swscanf_s(
            uuid.c_str(),
            L"%8lx-%4x-%4x-%2x%2x-%2x%2x%2x%2x%2x%2x",
            &data1, &data2, &data3, &d[0], &d[1], &d[2], &d[3], &d[4], &d[5], &d[6], &d[7]);
    GUID g{};
    g.Data1 = data1;
    g.Data2 = static_cast<unsigned short>(data2);
    g.Data3 = static_cast<unsigned short>(data3);
    for (int i = 0; i < 8; i++) {
        g.Data4[i] = static_cast<unsigned char>(d[i]);
    }
    return winrt::guid(g);
}

std::wstring FormatAddress(uint64_t address) {
    wchar_t buf[18];
    swprintf_s(
            buf,
            L"%02X:%02X:%02X:%02X:%02X:%02X",
            static_cast<unsigned>((address >> 40) & 0xFF),
            static_cast<unsigned>((address >> 32) & 0xFF),
            static_cast<unsigned>((address >> 24) & 0xFF),
            static_cast<unsigned>((address >> 16) & 0xFF),
            static_cast<unsigned>((address >> 8) & 0xFF),
            static_cast<unsigned>(address & 0xFF));
    return buf;
}

uint64_t ParseAddress(std::wstring const& s) {
    unsigned int b[6] = {0, 0, 0, 0, 0, 0};
    swscanf_s(s.c_str(), L"%2x:%2x:%2x:%2x:%2x:%2x", &b[0], &b[1], &b[2], &b[3], &b[4], &b[5]);
    uint64_t addr = 0;
    for (unsigned int i : b) {
        addr = (addr << 8) | (i & 0xFF);
    }
    return addr;
}

std::vector<uint8_t> ToVector(IBuffer const& buffer) {
    std::vector<uint8_t> data(buffer.Length());
    if (!data.empty()) {
        auto reader = DataReader::FromBuffer(buffer);
        reader.ReadBytes(data);
    }
    return data;
}

IBuffer ToBuffer(std::vector<uint8_t> const& data) {
    DataWriter writer;
    writer.WriteBytes(data);
    return writer.DetachBuffer();
}

}  // namespace

class PegasusBleBridge {
   public:
    PegasusBleBridge(JNIEnv* env, jobject thiz, std::wstring const& uartServiceUuid,
                      std::wstring const& writeCharUuid, std::wstring const& notifyCharUuid)
        : uartServiceUuid_(ParseGuid(uartServiceUuid)),
          writeCharUuid_(ParseGuid(writeCharUuid)),
          notifyCharUuid_(ParseGuid(notifyCharUuid)) {
        env->GetJavaVM(&jvm_);
        javaTransport_ = env->NewGlobalRef(thiz);
    }

    ~PegasusBleBridge() {
        JNIEnv* env = CurrentEnv();
        if (env != nullptr && javaTransport_ != nullptr) {
            env->DeleteGlobalRef(javaTransport_);
        }
    }

    void StartScan() {
        StopScan();
        watcher_ = BluetoothLEAdvertisementWatcher();
        watcher_.ScanningMode(BluetoothLEScanningMode::Active);
        // No service filter: unknown Pegasus units may not advertise the
        // UART service UUID directly - same rationale as
        // AndroidPegasusBleTransport/PegasusBleMac.m's unfiltered scans;
        // the user picks the device, GATT verifies it after connect.
        receivedToken_ = watcher_.Received({this, &PegasusBleBridge::OnAdvertisementReceived});
        try {
            watcher_.Start();
        } catch (winrt::hresult_error const& e) {
            ReportScanFailed(L"BLUETOOTH_DISABLED", e.message().c_str());
        }
    }

    void StopScan() {
        if (watcher_) {
            watcher_.Received(receivedToken_);
            if (watcher_.Status() == BluetoothLEAdvertisementWatcherStatus::Started) {
                watcher_.Stop();
            }
            watcher_ = nullptr;
        }
    }

    void Connect(std::wstring const& deviceId) {
        ReportConnectionState(L"CONNECTING");
        ConnectAsync(ParseAddress(deviceId));
    }

    void Disconnect() {
        if (connectionStatusToken_ && device_) {
            device_.ConnectionStatusChanged(connectionStatusToken_);
            connectionStatusToken_ = {};
        }
        if (notifyValueChangedToken_ && notifyChar_) {
            notifyChar_.ValueChanged(notifyValueChangedToken_);
            notifyValueChangedToken_ = {};
        }
        writeChar_ = nullptr;
        notifyChar_ = nullptr;
        if (device_) {
            device_.Close();
            device_ = nullptr;
        }
        // Reported explicitly rather than relying solely on
        // ConnectionStatusChanged, which is not guaranteed to fire from a
        // disconnect we ourselves initiated by closing our own reference -
        // see this file's header comment. Harmless if it also fires
        // natively afterward: WindowsPegasusBleTransport.handleNativeDisconnected()
        // already tolerates a duplicate DISCONNECTED report.
        ReportConnectionState(L"DISCONNECTED");
    }

    void Write(std::vector<uint8_t> const& data) { WriteAsync(data); }

   private:
    winrt::fire_and_forget ConnectAsync(uint64_t address) {
        auto lifetime = shared_from_this_workaround();
        try {
            auto dev = co_await BluetoothLEDevice::FromBluetoothAddressAsync(address);
            if (!dev) {
                ReportError(L"CONNECT_FAILED", L"Device not found - rescan required");
                ReportConnectionState(L"DISCONNECTED");
                co_return;
            }
            device_ = dev;
            connectionStatusToken_ =
                    device_.ConnectionStatusChanged({this, &PegasusBleBridge::OnConnectionStatusChanged});

            ReportConnectionState(L"DISCOVERING_SERVICES");
            auto servicesResult =
                    co_await device_.GetGattServicesForUuidAsync(uartServiceUuid_, BluetoothCacheMode::Uncached);
            if (servicesResult.Status() != GattCommunicationStatus::Success
                || servicesResult.Services().Size() == 0) {
                ReportError(L"SERVICE_NOT_FOUND", L"Nordic UART service not present on this device");
                Disconnect();
                co_return;
            }
            auto service = servicesResult.Services().GetAt(0);

            auto writeCharsResult =
                    co_await service.GetCharacteristicsForUuidAsync(writeCharUuid_, BluetoothCacheMode::Uncached);
            auto notifyCharsResult =
                    co_await service.GetCharacteristicsForUuidAsync(notifyCharUuid_, BluetoothCacheMode::Uncached);
            bool haveWrite = writeCharsResult.Status() == GattCommunicationStatus::Success
                              && writeCharsResult.Characteristics().Size() > 0;
            bool haveNotify = notifyCharsResult.Status() == GattCommunicationStatus::Success
                               && notifyCharsResult.Characteristics().Size() > 0;
            if (!haveWrite || !haveNotify) {
                wchar_t detail[64];
                swprintf_s(detail, L"write=%d notify=%d", haveWrite, haveNotify);
                ReportError(L"CHARACTERISTIC_NOT_FOUND", detail);
                Disconnect();
                co_return;
            }
            writeChar_ = writeCharsResult.Characteristics().GetAt(0);
            notifyChar_ = notifyCharsResult.Characteristics().GetAt(0);

            ReportConnectionState(L"SUBSCRIBING");
            auto notifyStatus = co_await notifyChar_.WriteClientCharacteristicConfigurationDescriptorAsync(
                    GattClientCharacteristicConfigurationDescriptorValue::Notify);
            if (notifyStatus != GattCommunicationStatus::Success) {
                ReportError(L"NOTIFICATION_SETUP_FAILED", L"CCCD write failed");
                Disconnect();
                co_return;
            }
            notifyValueChangedToken_ = notifyChar_.ValueChanged({this, &PegasusBleBridge::OnValueChanged});

            ReportConnectionState(L"CONNECTED");
        } catch (winrt::hresult_error const& e) {
            ReportError(L"CONNECT_FAILED", e.message().c_str());
            Disconnect();
        }
    }

    winrt::fire_and_forget WriteAsync(std::vector<uint8_t> data) {
        auto lifetime = shared_from_this_workaround();
        auto ch = writeChar_;
        if (!ch) {
            ReportError(L"WRITE_FAILED", L"Not connected");
            co_return;
        }
        try {
            // WRITE_TYPE_NO_RESPONSE: same choice as AndroidPegasusBleTransport.write()/
            // PegasusBleMac.m's writeData: (captured from the official DGT app via HCI
            // snoop on real hardware).
            auto status = co_await ch.WriteValueAsync(ToBuffer(data), GattWriteOption::WriteWithoutResponse);
            if (status == GattCommunicationStatus::Success) {
                ReportDataSent(data);
            } else {
                ReportError(L"WRITE_FAILED", L"WriteValueAsync failed");
            }
        } catch (winrt::hresult_error const& e) {
            ReportError(L"WRITE_FAILED", e.message().c_str());
        }
    }

    // fire_and_forget coroutines don't keep 'this' alive on their own; the
    // bridge itself is kept alive by MacosPegasusBleTransport-equivalent
    // Java-side ownership (one bridge per WindowsPegasusBleTransport
    // instance, destroyed only via nativeDestroy), so no extra lifetime
    // management is needed here - this helper exists purely as a
    // documentation anchor for that assumption.
    PegasusBleBridge* shared_from_this_workaround() { return this; }

    void OnAdvertisementReceived(BluetoothLEAdvertisementWatcher const&,
                                  BluetoothLEAdvertisementReceivedEventArgs const& args) {
        ReportDeviceFound(FormatAddress(args.BluetoothAddress()), args.Advertisement().LocalName(),
                           args.RawSignalStrengthInDBm());
    }

    void OnConnectionStatusChanged(BluetoothLEDevice const& sender, winrt::Windows::Foundation::IInspectable const&) {
        if (sender.ConnectionStatus() == BluetoothConnectionStatus::Disconnected) {
            ReportConnectionState(L"DISCONNECTED");
        }
    }

    void OnValueChanged(GattCharacteristic const&, GattValueChangedEventArgs const& args) {
        ReportDataReceived(ToVector(args.CharacteristicValue()));
    }

    // ---- JNI callback dispatch --------------------------------------------

    JNIEnv* CurrentEnv() {
        JNIEnv* env = nullptr;
        jint result = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            if (jvm_->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
                return nullptr;
            }
        } else if (result != JNI_OK) {
            return nullptr;
        }
        return env;
    }

    void ReportDeviceFound(std::wstring const& address, std::wstring const& name, int rssi) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid =
                env->GetMethodID(cls, "onNativeDeviceFound", "(Ljava/lang/String;Ljava/lang/String;I)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jstring jAddress = env->NewStringUTF(ToUtf8(address).c_str());
        jstring jName = name.empty() ? nullptr : env->NewStringUTF(ToUtf8(name).c_str());
        env->CallVoidMethod(javaTransport_, mid, jAddress, jName, static_cast<jint>(rssi));
        env->DeleteLocalRef(jAddress);
        if (jName != nullptr) {
            env->DeleteLocalRef(jName);
        }
    }

    void ReportConnectionState(std::wstring const& state) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid = env->GetMethodID(cls, "onNativeConnectionStateChanged", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jstring jState = env->NewStringUTF(ToUtf8(state).c_str());
        env->CallVoidMethod(javaTransport_, mid, jState);
        env->DeleteLocalRef(jState);
    }

    void ReportError(std::wstring const& code, std::wstring const& detail) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid = env->GetMethodID(cls, "onNativeError", "(Ljava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jstring jCode = env->NewStringUTF(ToUtf8(code).c_str());
        jstring jDetail = env->NewStringUTF(ToUtf8(detail).c_str());
        env->CallVoidMethod(javaTransport_, mid, jCode, jDetail);
        env->DeleteLocalRef(jCode);
        env->DeleteLocalRef(jDetail);
    }

    void ReportScanFailed(std::wstring const& code, std::wstring const& detail) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid = env->GetMethodID(cls, "onNativeScanFailed", "(Ljava/lang/String;Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jstring jCode = env->NewStringUTF(ToUtf8(code).c_str());
        jstring jDetail = env->NewStringUTF(ToUtf8(detail).c_str());
        env->CallVoidMethod(javaTransport_, mid, jCode, jDetail);
        env->DeleteLocalRef(jCode);
        env->DeleteLocalRef(jDetail);
    }

    void ReportDataReceived(std::vector<uint8_t> const& data) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid = env->GetMethodID(cls, "onNativeDataReceived", "([B)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jbyteArray arr = env->NewByteArray(static_cast<jsize>(data.size()));
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
        env->CallVoidMethod(javaTransport_, mid, arr);
        env->DeleteLocalRef(arr);
    }

    void ReportDataSent(std::vector<uint8_t> const& data) {
        JNIEnv* env = CurrentEnv();
        if (env == nullptr) {
            return;
        }
        jclass cls = env->GetObjectClass(javaTransport_);
        jmethodID mid = env->GetMethodID(cls, "onNativeDataSent", "([B)V");
        env->DeleteLocalRef(cls);
        if (mid == nullptr) {
            env->ExceptionClear();
            return;
        }
        jbyteArray arr = env->NewByteArray(static_cast<jsize>(data.size()));
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
        env->CallVoidMethod(javaTransport_, mid, arr);
        env->DeleteLocalRef(arr);
    }

    JavaVM* jvm_ = nullptr;
    jobject javaTransport_ = nullptr;
    winrt::guid uartServiceUuid_;
    winrt::guid writeCharUuid_;
    winrt::guid notifyCharUuid_;
    BluetoothLEAdvertisementWatcher watcher_{nullptr};
    winrt::event_token receivedToken_{};
    BluetoothLEDevice device_{nullptr};
    winrt::event_token connectionStatusToken_{};
    GattCharacteristic writeChar_{nullptr};
    GattCharacteristic notifyChar_{nullptr};
    winrt::event_token notifyValueChangedToken_{};
};

// ---- JNI exports ---------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeCreate(
        JNIEnv* env, jobject thiz, jstring uartServiceUuid, jstring writeCharUuid, jstring notifyCharUuid) {
    static bool apartmentInitialized = false;
    if (!apartmentInitialized) {
        winrt::init_apartment(winrt::apartment_type::multi_threaded);
        apartmentInitialized = true;
    }
    auto* bridge = new PegasusBleBridge(env, thiz, JStringToWString(env, uartServiceUuid),
                                         JStringToWString(env, writeCharUuid),
                                         JStringToWString(env, notifyCharUuid));
    return reinterpret_cast<jlong>(bridge);
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeDestroy(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<PegasusBleBridge*>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeStartScan(
        JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<PegasusBleBridge*>(handle)->StartScan();
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeStopScan(
        JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<PegasusBleBridge*>(handle)->StopScan();
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeConnect(
        JNIEnv* env, jclass, jlong handle, jstring deviceId) {
    reinterpret_cast<PegasusBleBridge*>(handle)->Connect(JStringToWString(env, deviceId));
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeDisconnect(
        JNIEnv*, jclass, jlong handle) {
    reinterpret_cast<PegasusBleBridge*>(handle)->Disconnect();
}

extern "C" JNIEXPORT void JNICALL
Java_de_schliweb_moveapiece_desktop_pegasus_WindowsPegasusBleTransport_nativeWrite(
        JNIEnv* env, jclass, jlong handle, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    std::vector<uint8_t> vec(reinterpret_cast<uint8_t*>(bytes), reinterpret_cast<uint8_t*>(bytes) + len);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    reinterpret_cast<PegasusBleBridge*>(handle)->Write(vec);
}

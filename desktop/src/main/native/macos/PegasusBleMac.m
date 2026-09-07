/*
 * Copyright (C) 2026 Christian Kierdorf
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/*
 * macOS CoreBluetooth backend for MacosPegasusBleTransport (see that class's
 * Javadoc). Raw bytes only - no DGT message interpretation here, mirroring
 * app/src/main/java/de/schliweb/pegasus/bluetooth/AndroidPegasusBleTransport.java's
 * own phase-1 boundary; pegasus-core's PegasusGameBridge-equivalent owns
 * everything above that.
 *
 * Threading: CBCentralManager is created with dispatch_get_main_queue() as
 * its delegate queue rather than a private GCD queue. On macOS, JavaFX's
 * Application Thread *is* the process's real main thread (Cocoa requires UI
 * on the actual main thread, so Glass pins it there) and keeps the Cocoa run
 * loop pumping for the whole time the app is alive - so every delegate
 * callback below already lands on that same thread, which is also the
 * thread the JVM itself launched on and therefore already attached. See
 * currentEnv() below for the (defensive, rarely-taken) fallback path in case
 * that assumption is ever violated.
 */

#import <CoreBluetooth/CoreBluetooth.h>
#import <Foundation/Foundation.h>
#import <jni.h>

@interface PegasusBleBridge : NSObject <CBCentralManagerDelegate, CBPeripheralDelegate>

@property(nonatomic, strong) CBCentralManager *central;
@property(nonatomic, strong) NSMutableDictionary<NSString *, CBPeripheral *> *peripheralsById;
@property(nonatomic, strong) CBPeripheral *connectedPeripheral;
@property(nonatomic, strong) CBCharacteristic *writeCharacteristic;
@property(nonatomic, strong) CBCharacteristic *notifyCharacteristic;
@property(nonatomic, strong) CBUUID *uartServiceUuid;
@property(nonatomic, strong) CBUUID *writeCharUuid;
@property(nonatomic, strong) CBUUID *notifyCharUuid;
@property(nonatomic, assign) JavaVM *jvm;
@property(nonatomic, assign) jobject javaTransport;

- (instancetype)initWithEnv:(JNIEnv *)env
                        thiz:(jobject)thiz
             uartServiceUuid:(NSString *)uartServiceUuid
               writeCharUuid:(NSString *)writeCharUuid
              notifyCharUuid:(NSString *)notifyCharUuid;

- (JNIEnv *)currentEnv;
- (void)startScan;
- (void)stopScan;
- (void)connectToDeviceId:(NSString *)deviceId;
- (void)disconnect;
- (void)writeData:(NSData *)data;

@end

// ---- Java callback dispatch --------------------------------------------

static void reportDeviceFound(PegasusBleBridge *bridge, NSString *address, NSString *name, int rssi) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid =
            (*env)->GetMethodID(env, cls, "onNativeDeviceFound", "(Ljava/lang/String;Ljava/lang/String;I)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jstring jAddress = (*env)->NewStringUTF(env, address.UTF8String);
    jstring jName = name != nil ? (*env)->NewStringUTF(env, name.UTF8String) : NULL;
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, jAddress, jName, (jint)rssi);
    (*env)->DeleteLocalRef(env, jAddress);
    if (jName != NULL) (*env)->DeleteLocalRef(env, jName);
}

static void reportConnectionState(PegasusBleBridge *bridge, NSString *state) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid =
            (*env)->GetMethodID(env, cls, "onNativeConnectionStateChanged", "(Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jstring jState = (*env)->NewStringUTF(env, state.UTF8String);
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, jState);
    (*env)->DeleteLocalRef(env, jState);
}

static void reportError(PegasusBleBridge *bridge, NSString *code, NSString *detail) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid =
            (*env)->GetMethodID(env, cls, "onNativeError", "(Ljava/lang/String;Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jstring jCode = (*env)->NewStringUTF(env, code.UTF8String);
    jstring jDetail = (*env)->NewStringUTF(env, (detail ?: @"").UTF8String);
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, jCode, jDetail);
    (*env)->DeleteLocalRef(env, jCode);
    (*env)->DeleteLocalRef(env, jDetail);
}

static void reportScanFailed(PegasusBleBridge *bridge, NSString *code, NSString *detail) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid =
            (*env)->GetMethodID(env, cls, "onNativeScanFailed", "(Ljava/lang/String;Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jstring jCode = (*env)->NewStringUTF(env, code.UTF8String);
    jstring jDetail = (*env)->NewStringUTF(env, (detail ?: @"").UTF8String);
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, jCode, jDetail);
    (*env)->DeleteLocalRef(env, jCode);
    (*env)->DeleteLocalRef(env, jDetail);
}

static void reportDataReceived(PegasusBleBridge *bridge, NSData *data) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onNativeDataReceived", "([B)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)data.length);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)data.length, (const jbyte *)data.bytes);
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, arr);
    (*env)->DeleteLocalRef(env, arr);
}

static void reportDataSent(PegasusBleBridge *bridge, NSData *data) {
    JNIEnv *env = [bridge currentEnv];
    if (env == NULL) return;
    jclass cls = (*env)->GetObjectClass(env, bridge.javaTransport);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onNativeDataSent", "([B)V");
    (*env)->DeleteLocalRef(env, cls);
    if (mid == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)data.length);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)data.length, (const jbyte *)data.bytes);
    (*env)->CallVoidMethod(env, bridge.javaTransport, mid, arr);
    (*env)->DeleteLocalRef(env, arr);
}

// ---- PegasusBleBridge ---------------------------------------------------

@implementation PegasusBleBridge

- (instancetype)initWithEnv:(JNIEnv *)env
                        thiz:(jobject)thiz
             uartServiceUuid:(NSString *)uartServiceUuid
               writeCharUuid:(NSString *)writeCharUuid
              notifyCharUuid:(NSString *)notifyCharUuid {
    self = [super init];
    if (self != nil) {
        (*env)->GetJavaVM(env, &_jvm);
        _javaTransport = (*env)->NewGlobalRef(env, thiz);
        _peripheralsById = [NSMutableDictionary dictionary];
        _uartServiceUuid = [CBUUID UUIDWithString:uartServiceUuid];
        _writeCharUuid = [CBUUID UUIDWithString:writeCharUuid];
        _notifyCharUuid = [CBUUID UUIDWithString:notifyCharUuid];
        _central = [[CBCentralManager alloc] initWithDelegate:self queue:dispatch_get_main_queue()];
    }
    return self;
}

- (void)dealloc {
    if (_jvm != NULL && _javaTransport != NULL) {
        JNIEnv *env = NULL;
        if ((*_jvm)->GetEnv(_jvm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) {
            (*env)->DeleteGlobalRef(env, _javaTransport);
        }
    }
}

- (JNIEnv *)currentEnv {
    JNIEnv *env = NULL;
    jint result = (*_jvm)->GetEnv(_jvm, (void **)&env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        // Defensive fallback only - see the threading note at the top of this
        // file for why every real callback is expected to already be
        // attached (it runs on the JVM's own launch/main thread). Never
        // detached again: this may be a GCD worker thread CoreBluetooth
        // reuses across calls, and repeated attach/detach churn is wasted
        // work for a callback rate this low (board writes/notifications).
        if ((*_jvm)->AttachCurrentThreadAsDaemon(_jvm, (void **)&env, NULL) != JNI_OK) {
            return NULL;
        }
    } else if (result != JNI_OK) {
        return NULL;
    }
    return env;
}

- (void)startScan {
    if (self.central.state != CBManagerStatePoweredOn) {
        reportScanFailed(self, @"BLUETOOTH_DISABLED", @"Bluetooth is not powered on");
        return;
    }
    [self.peripheralsById removeAllObjects];
    // No service filter: unknown Pegasus units may not advertise the UART
    // service UUID directly - same rationale as AndroidPegasusBleTransport's
    // unfiltered scan; the user picks the device, GATT verifies it after
    // connect.
    [self.central scanForPeripheralsWithServices:nil
                                          options:@{CBCentralManagerScanOptionAllowDuplicatesKey : @NO}];
}

- (void)stopScan {
    [self.central stopScan];
}

- (void)connectToDeviceId:(NSString *)deviceId {
    if (self.central.state != CBManagerStatePoweredOn) {
        reportError(self, @"BLUETOOTH_DISABLED", @"Bluetooth is not powered on");
        return;
    }
    CBPeripheral *peripheral = self.peripheralsById[deviceId];
    if (peripheral == nil) {
        reportError(self, @"CONNECT_FAILED", @"Device not found - rescan required");
        return;
    }
    [self.central stopScan];
    peripheral.delegate = self;
    self.connectedPeripheral = peripheral;
    self.writeCharacteristic = nil;
    self.notifyCharacteristic = nil;
    reportConnectionState(self, @"CONNECTING");
    [self.central connectPeripheral:peripheral options:nil];
}

- (void)disconnect {
    if (self.connectedPeripheral != nil) {
        [self.central cancelPeripheralConnection:self.connectedPeripheral];
    }
}

- (void)writeData:(NSData *)data {
    CBPeripheral *peripheral = self.connectedPeripheral;
    CBCharacteristic *characteristic = self.writeCharacteristic;
    if (peripheral == nil || characteristic == nil) {
        reportError(self, @"WRITE_FAILED", @"Not connected");
        return;
    }
    // WRITE_TYPE_NO_RESPONSE: same choice as AndroidPegasusBleTransport.write()
    // (captured from the official DGT app via HCI snoop on real hardware).
    // CoreBluetooth never calls back for this write type (unlike Android's
    // onCharacteristicWrite, which fires for both types) - report success
    // immediately; the reply, if any, arrives separately as a notification.
    [peripheral writeValue:data forCharacteristic:characteristic type:CBCharacteristicWriteWithoutResponse];
    reportDataSent(self, data);
}

#pragma mark - CBCentralManagerDelegate

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    // No action needed here: startScan/connectToDeviceId check
    // central.state synchronously and report BLUETOOTH_DISABLED themselves.
}

- (void)centralManager:(CBCentralManager *)central
    didDiscoverPeripheral:(CBPeripheral *)peripheral
        advertisementData:(NSDictionary<NSString *, id> *)advertisementData
                     RSSI:(NSNumber *)RSSI {
    NSString *deviceId = peripheral.identifier.UUIDString;
    self.peripheralsById[deviceId] = peripheral;
    NSString *name = advertisementData[CBAdvertisementDataLocalNameKey];
    if (name == nil) {
        name = peripheral.name;
    }
    reportDeviceFound(self, deviceId, name, RSSI.intValue);
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
    reportConnectionState(self, @"DISCOVERING_SERVICES");
    [peripheral discoverServices:@[ self.uartServiceUuid ]];
}

- (void)centralManager:(CBCentralManager *)central
    didFailToConnectPeripheral:(CBPeripheral *)peripheral
                          error:(NSError *)error {
    reportError(self, @"CONNECT_FAILED", error.localizedDescription ?: @"connect failed");
    reportConnectionState(self, @"DISCONNECTED");
}

- (void)centralManager:(CBCentralManager *)central
    didDisconnectPeripheral:(CBPeripheral *)peripheral
                       error:(NSError *)error {
    self.writeCharacteristic = nil;
    self.notifyCharacteristic = nil;
    reportConnectionState(self, @"DISCONNECTED");
}

#pragma mark - CBPeripheralDelegate

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(NSError *)error {
    if (error != nil) {
        reportError(self, @"SERVICE_NOT_FOUND", error.localizedDescription);
        [self.central cancelPeripheralConnection:peripheral];
        return;
    }
    CBService *uart = nil;
    for (CBService *service in peripheral.services) {
        if ([service.UUID isEqual:self.uartServiceUuid]) {
            uart = service;
            break;
        }
    }
    if (uart == nil) {
        reportError(self, @"SERVICE_NOT_FOUND", @"Nordic UART service not present on this device");
        [self.central cancelPeripheralConnection:peripheral];
        return;
    }
    [peripheral discoverCharacteristics:@[ self.writeCharUuid, self.notifyCharUuid ] forService:uart];
}

- (void)peripheral:(CBPeripheral *)peripheral
    didDiscoverCharacteristicsForService:(CBService *)service
                                   error:(NSError *)error {
    if (error != nil) {
        reportError(self, @"CHARACTERISTIC_NOT_FOUND", error.localizedDescription);
        [self.central cancelPeripheralConnection:peripheral];
        return;
    }
    for (CBCharacteristic *characteristic in service.characteristics) {
        if ([characteristic.UUID isEqual:self.writeCharUuid]) {
            self.writeCharacteristic = characteristic;
        } else if ([characteristic.UUID isEqual:self.notifyCharUuid]) {
            self.notifyCharacteristic = characteristic;
        }
    }
    if (self.writeCharacteristic == nil || self.notifyCharacteristic == nil) {
        reportError(self, @"CHARACTERISTIC_NOT_FOUND",
                    [NSString stringWithFormat:@"write=%d notify=%d", self.writeCharacteristic != nil,
                                                self.notifyCharacteristic != nil]);
        [self.central cancelPeripheralConnection:peripheral];
        return;
    }
    reportConnectionState(self, @"SUBSCRIBING");
    [peripheral setNotifyValue:YES forCharacteristic:self.notifyCharacteristic];
}

- (void)peripheral:(CBPeripheral *)peripheral
    didUpdateNotificationStateForCharacteristic:(CBCharacteristic *)characteristic
                                           error:(NSError *)error {
    if (error != nil) {
        reportError(self, @"NOTIFICATION_SETUP_FAILED", error.localizedDescription);
        [self.central cancelPeripheralConnection:peripheral];
        return;
    }
    reportConnectionState(self, @"CONNECTED");
}

- (void)peripheral:(CBPeripheral *)peripheral
    didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic
                               error:(NSError *)error {
    if (error != nil || characteristic.value == nil) {
        return;
    }
    if ([characteristic.UUID isEqual:self.notifyCharUuid]) {
        reportDataReceived(self, characteristic.value);
    }
}

- (void)peripheral:(CBPeripheral *)peripheral
    didWriteValueForCharacteristic:(CBCharacteristic *)characteristic
                               error:(NSError *)error {
    // Only fires for CBCharacteristicWriteWithResponse; writeData: above
    // always uses WriteWithoutResponse, so this is not expected to be
    // called - kept only so a future write-with-response path (none today)
    // wouldn't silently go unhandled.
    if (error != nil) {
        reportError(self, @"WRITE_FAILED", error.localizedDescription);
    }
}

@end

// ---- JNI exports ---------------------------------------------------------

JNIEXPORT jlong JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeCreate(
        JNIEnv *env, jobject thiz, jstring uartServiceUuid, jstring writeCharUuid, jstring notifyCharUuid) {
    const char *uartC = (*env)->GetStringUTFChars(env, uartServiceUuid, NULL);
    const char *writeC = (*env)->GetStringUTFChars(env, writeCharUuid, NULL);
    const char *notifyC = (*env)->GetStringUTFChars(env, notifyCharUuid, NULL);
    PegasusBleBridge *bridge;
    @autoreleasepool {
        bridge = [[PegasusBleBridge alloc] initWithEnv:env
                                                   thiz:thiz
                                        uartServiceUuid:[NSString stringWithUTF8String:uartC]
                                          writeCharUuid:[NSString stringWithUTF8String:writeC]
                                         notifyCharUuid:[NSString stringWithUTF8String:notifyC]];
    }
    (*env)->ReleaseStringUTFChars(env, uartServiceUuid, uartC);
    (*env)->ReleaseStringUTFChars(env, writeCharUuid, writeC);
    (*env)->ReleaseStringUTFChars(env, notifyCharUuid, notifyC);
    return (jlong)(intptr_t)(__bridge_retained void *)bridge;
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeDestroy(
        JNIEnv *env, jclass clazz, jlong handle) {
    @autoreleasepool {
        PegasusBleBridge *bridge = (__bridge_transfer PegasusBleBridge *)(void *)handle;
        (void)bridge;
    }
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeStartScan(
        JNIEnv *env, jclass clazz, jlong handle) {
    PegasusBleBridge *bridge = (__bridge PegasusBleBridge *)(void *)handle;
    [bridge startScan];
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeStopScan(
        JNIEnv *env, jclass clazz, jlong handle) {
    PegasusBleBridge *bridge = (__bridge PegasusBleBridge *)(void *)handle;
    [bridge stopScan];
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeConnect(
        JNIEnv *env, jclass clazz, jlong handle, jstring deviceId) {
    PegasusBleBridge *bridge = (__bridge PegasusBleBridge *)(void *)handle;
    const char *idC = (*env)->GetStringUTFChars(env, deviceId, NULL);
    NSString *idStr = [NSString stringWithUTF8String:idC];
    (*env)->ReleaseStringUTFChars(env, deviceId, idC);
    [bridge connectToDeviceId:idStr];
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeDisconnect(
        JNIEnv *env, jclass clazz, jlong handle) {
    PegasusBleBridge *bridge = (__bridge PegasusBleBridge *)(void *)handle;
    [bridge disconnect];
}

JNIEXPORT void JNICALL Java_de_schliweb_moveapiece_desktop_pegasus_MacosPegasusBleTransport_nativeWrite(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray data) {
    PegasusBleBridge *bridge = (__bridge PegasusBleBridge *)(void *)handle;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    NSData *nsData = [NSData dataWithBytes:bytes length:(NSUInteger)len];
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    [bridge writeData:nsData];
}

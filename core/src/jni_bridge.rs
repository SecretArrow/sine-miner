//! JNI bridge — exposes Rust core functions to Kotlin via JNI.
//!
//! Convention:
//! - All exported functions follow `Java_com_secretarrow_sineminer_core_native_NativeBridge_<method>`
//! - Errors return `null` or `0` and write an error message to the last `JString` out param.
//! - Strings are UTF-8; bytes are `jbyteArray`.
//! - The native library is `libsineminer_core.so` on Android.

#![allow(clippy::missing_safety_doc)]

use std::ffi::CString;
use std::sync::OnceLock;

use jni::objects::{
    JByteArray, JClass, JString, JValue, JObject, JNIEnv, GlobalRef,
};
use jni::sys::{jbyte, jbyteArray, jdouble, jint, jlong, jobject, jstring, JNI_FALSE, JNI_TRUE};
use jni::strings::JNIString;

use crate::wallet::{Wallet, WalletConfig, Mnemonic, WordCount};

/// Initialize logger once.
static LOG_INIT: OnceLock<()> = OnceLock::new();

fn init_logger_once() {
    LOG_INIT.get_or_init(|| {
        let _ = env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
            .try_init();
    });
}

/// Convert a Rust string to a Java String. Returns null on failure.
fn to_jstring<'local>(env: &mut JNIEnv<'local>, s: &str) -> JString<'local> {
    match env.new_string(s) {
        Ok(jstr) => jstr,
        Err(_) => {
            // Fallback: empty string
            env.new_string("").unwrap_or_else(|_| unsafe { std::mem::zeroed() })
        }
    }
}

/// Helper to return an error result as a Java String (or null).
fn error_to_jstring<'local>(env: &mut JNIEnv<'local>, e: &dyn std::error::Error) -> JString<'local> {
    to_jstring(env, &e.to_string())
}

/// Java exception helper. Throws a RuntimeException with the given message.
fn throw_runtime<'local>(env: &mut JNIEnv<'local>, msg: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", msg);
}

// =============================================================================
// Wallet — create, import, encrypt, decrypt, get address
// =============================================================================

/// Create a brand-new wallet.
///
/// Java signature:
/// ```java
/// public static native String[] createWallet(String label, int wordCount, String networkId);
/// ```
/// Returns `String[2] = {mnemonic_phrase, address}` or throws.
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_createWallet(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    label: JString<'local>,
    word_count: jint,
    network_id: JString<'local>,
) -> jobject {
    init_logger_once();

    let label: String = match env.get_string(&label) {
        Ok(s) => s.into(),
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            return JObject::null().into_raw();
        }
    };
    let network_id: String = match env.get_string(&network_id) {
        Ok(s) => s.into(),
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            return JObject::null().into_raw();
        }
    };

    let wc = match word_count {
        12 => WordCount::Words12,
        15 => WordCount::Words15,
        18 => WordCount::Words18,
        21 => WordCount::Words21,
        24 => WordCount::Words24,
        _ => WordCount::Words24,
    };

    let config = WalletConfig {
        network_id: network_id.clone(),
        ..Default::default()
    };

    match Wallet::create(&label, wc, config) {
        Ok((wallet, mnemonic)) => {
            let phrase = mnemonic.to_phrase();
            let address = wallet.address_string();

            let arr = match env.new_object_array(
                2,
                "java/lang/String",
                JObject::null(),
            ) {
                Ok(a) => a,
                Err(e) => {
                    throw_runtime(&mut env, &e.to_string());
                    return JObject::null().into_raw();
                }
            };

            let phrase_j = match env.new_string(&phrase) {
                Ok(s) => s,
                Err(e) => {
                    throw_runtime(&mut env, &e.to_string());
                    return JObject::null().into_raw();
                }
            };
            let address_j = match env.new_string(&address) {
                Ok(s) => s,
                Err(e) => {
                    throw_runtime(&mut env, &e.to_string());
                    return JObject::null().into_raw();
                }
            };

            let _ = env.set_object_array_element(&arr, 0, &phrase_j);
            let _ = env.set_object_array_element(&arr, 1, &address_j);

            arr.into_raw()
        }
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            JObject::null().into_raw()
        }
    }
}

/// Import wallet from mnemonic.
///
/// Java signature:
/// ```java
/// public static native String importWallet(String label, String mnemonic, String passphrase, String networkId);
/// ```
/// Returns the address or throws.
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_importWallet(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    label: JString<'local>,
    mnemonic: JString<'local>,
    passphrase: JString<'local>,
    network_id: JString<'local>,
) -> jstring {
    init_logger_once();

    let label: String = env.get_string(&label).map(Into::into).unwrap_or_default();
    let mnemonic: String = env.get_string(&mnemonic).map(Into::into).unwrap_or_default();
    let passphrase: String = env.get_string(&passphrase).map(Into::into).unwrap_or_default();
    let network_id: String = env.get_string(&network_id).map(Into::into).unwrap_or_default();

    let config = WalletConfig {
        network_id,
        ..Default::default()
    };

    match Wallet::import(&label, &mnemonic, &passphrase, config) {
        Ok(wallet) => {
            let addr = wallet.address_string();
            let jstr = env.new_string(&addr).unwrap_or_else(|_| {
                throw_runtime(&mut env, "failed to allocate address string");
                JString::from(JObject::null())
            });
            jstr.into_raw()
        }
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            std::ptr::null_mut()
        }
    }
}

/// Validate a Blocknet address string.
///
/// Java signature:
/// ```java
/// public static native boolean validateAddress(String address);
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_validateAddress(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    address: JString<'local>,
) -> jint {
    init_logger_once();

    let address: String = env.get_string(&address).map(Into::into).unwrap_or_default();
    if crate::wallet::StealthAddress::validate(&address).is_ok() {
        JNI_TRUE as jint
    } else {
        JNI_FALSE as jint
    }
}

/// Encrypt a wallet to a JSON blob (so the app can store it).
///
/// Java signature:
/// ```java
/// public static native String encryptWallet(String label, String mnemonic, String passphrase, String password, String networkId);
/// ```
/// Returns the encrypted keystore JSON or throws.
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_encryptWallet(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    label: JString<'local>,
    mnemonic: JString<'local>,
    passphrase: JString<'local>,
    password: JString<'local>,
    network_id: JString<'local>,
) -> jstring {
    init_logger_once();

    let label: String = env.get_string(&label).map(Into::into).unwrap_or_default();
    let mnemonic: String = env.get_string(&mnemonic).map(Into::into).unwrap_or_default();
    let passphrase: String = env.get_string(&passphrase).map(Into::into).unwrap_or_default();
    let password: String = env.get_string(&password).map(Into::into).unwrap_or_default();
    let network_id: String = env.get_string(&network_id).map(Into::into).unwrap_or_default();

    let config = WalletConfig { network_id, ..Default::default() };

    let result: Result<String, String> = (|| {
        let wallet = Wallet::import(&label, &mnemonic, &passphrase, config).map_err(|e| e.to_string())?;
        let encrypted = wallet.encrypt(&password).map_err(|e| e.to_string())?;
        encrypted.to_json().map_err(|e| e.to_string())
    })();

    match result {
        Ok(json) => env.new_string(&json).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

// =============================================================================
// Mining — start, stop, hashrate snapshot
// =============================================================================

/// Start the mining engine. Returns a `jlong` handle to the engine instance.
///
/// Java signature:
/// ```java
/// public static native long startMining(int memoryKib, int threads, long nonceSeed);
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_startMining(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    memory_kib: jint,
    threads: jint,
    nonce_seed: jlong,
) -> jlong {
    init_logger_once();

    let config = crate::mining::MiningConfig {
        memory_kib: memory_kib as u32,
        threads: threads as u32,
        nonce_seed: nonce_seed as u64,
        ..Default::default()
    };

    match crate::mining::MiningEngine::new(config) {
        Ok(engine) => Box::into_raw(Box::new(engine)) as jlong,
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            0
        }
    }
}

/// Stop the mining engine and free resources.
///
/// Java signature:
/// ```java
/// public static native void stopMining(long handle);
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_stopMining(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        let _ = Box::from_raw(handle as *mut crate::mining::MiningEngine);
    }
}

/// Compute one Argon2id hash for testing.
///
/// Java signature:
/// ```java
/// public static native byte[] hashOnce(long handle, byte[] headerBase, long nonce);
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_hashOnce(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    header_base: JByteArray<'local>,
    nonce: jlong,
) -> jbyteArray {
    if handle == 0 {
        throw_runtime(&mut env, "invalid mining handle");
        return std::ptr::null_mut();
    }
    let engine = unsafe { &*(handle as *const crate::mining::MiningEngine) };
    let header_bytes = match env.get_byte_array_elements(&header_base, jni::objects::ReleaseMode::Copy) {
        Ok(b) => b,
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            return std::ptr::null_mut();
        }
    };
    let header_vec: Vec<u8> = header_bytes.iter().map(|b| *b as u8).collect();
    match engine.hash_once(&header_vec, nonce as u64) {
        Ok(result) => {
            match env.byte_array_from_slice(&result.hash) {
                Ok(arr) => arr.into_raw(),
                Err(e) => {
                    throw_runtime(&mut env, &e.to_string());
                    std::ptr::null_mut()
                }
            }
        }
        Err(e) => {
            throw_runtime(&mut env, &e.to_string());
            std::ptr::null_mut()
        }
    }
}

/// Check whether a hash meets a target.
///
/// Java signature:
/// ```java
/// public static native boolean meetsTarget(byte[] hash, byte[] target);
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_meetsTarget(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JByteArray<'local>,
    target: JByteArray<'local>,
) -> jint {
    let hash_bytes = match env.get_byte_array_elements(&hash, jni::objects::ReleaseMode::Copy) {
        Ok(b) => b,
        Err(_) => return JNI_FALSE as jint,
    };
    let target_bytes = match env.get_byte_array_elements(&target, jni::objects::ReleaseMode::Copy) {
        Ok(b) => b,
        Err(_) => return JNI_FALSE as jint,
    };
    let hash_vec: Vec<u8> = hash_bytes.iter().map(|b| *b as u8).collect();
    let target_vec: Vec<u8> = target_bytes.iter().map(|b| *b as u8).collect();
    if hash_vec.len() != 32 || target_vec.len() != 32 {
        return JNI_FALSE as jint;
    }
    if crate::mining::target::meets_target(&hash_vec, &target_vec) {
        JNI_TRUE as jint
    } else {
        JNI_FALSE as jint
    }
}

/// Get the core library version.
///
/// Java signature:
/// ```java
/// public static native String coreVersion();
/// ```
#[no_mangle]
pub extern "system" fn Java_com_secretarrow_sineminer_core_native_NativeBridge_coreVersion(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let v = crate::CORE_VERSION;
    match env.new_string(v) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI entry point — called when the library is loaded by the JVM.
#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: jni::JavaVM, _reserved: *mut std::ffi::c_void) -> jint {
    init_logger_once();
    log::info!("sineminer_core loaded");
    jni::sys::JNI_VERSION_1_6 as jint
}

/// JNI exit point — called when the JVM unloads the library.
#[no_mangle]
pub extern "system" fn JNI_OnUnload(_vm: jni::JavaVM, _reserved: *mut std::ffi::c_void) {
    log::info!("sineminer_core unloading");
}

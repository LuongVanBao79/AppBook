#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_appbook_MyApplication_getApiSecretFromNative(
        JNIEnv* env,
        jobject /* this */) {

    std::string part1 = "bQzV_";
    std::string part2 = "ZZp3F9e";
    std::string part3 = "yonEWh";
    std::string part4 = "nn9vcqQts";


    std::string total = part1 + part2 + part3 + part4;

    return env->NewStringUTF(total.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_appbook_utils_EncryptionHelper_getAesKeyFromNative(
        JNIEnv* env,
        jobject /* this */) {

    // Key AES 32 ký tự: "ChangeMeDemoKey123ChangeMeDemoKey123"
    // Cắt nhỏ ra để hacker không scan thấy chuỗi liền mạch
    std::string p1 = "ChangeMe";
    std::string p2 = "DemoKey123";
    std::string p3 = "ChangeMe";
    std::string p4 = "DemoKey123";

    // Ghép lại
    std::string total = p1 + p2 + p3 + p4;

    return env->NewStringUTF(total.c_str());
}

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files) $(call all-renderscript-files-under, java)

LOCAL_PACKAGE_NAME := SecondScreen
LOCAL_CERTIFICATE := shared

include $(BUILD_PACKAGE)

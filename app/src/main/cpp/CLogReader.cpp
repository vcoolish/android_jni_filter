#include <jni.h>
#include "CLogReader.h"

namespace {
    bool IsStringMatchedFilter(const char *str, const char *filter) {

        const char *cp = nullptr;
        const char *mp = nullptr;

        while ((*str) && (*filter != '*')) {
            if ((*filter != *str) && (*filter != '?')) {
                return false;
            }
            ++str;
            ++filter;
        }

        while (*str) {
            if (*filter == '*') {
                if (!*++filter) {
                    return true;
                }

                mp = filter;
                cp = str + 1;
            } else if ((*filter == *str) || (*filter == '?')) {
                ++filter;
                ++str;
            } else {
                filter = mp;
                str = cp++;
            }
        }

        while (*filter == '*') {
            ++filter;
        }

        return static_cast<bool>(!*filter);
    }
}

CLogReader::CLogReader(const char *filename)
        : filename_(filename),
          file_(nullptr),
          fileOpened_(false),
          filter_(nullptr) {

}

CLogReader::~CLogReader() {
    Close();

    free(filter_);
}

bool CLogReader::Open() {
    bool result;

    file_ = fopen(filename_, "rb");
    if (file_ != NULL) {
        result = true;
    } else {
        file_ = nullptr;
        result = false;
    }

    return result;
}

void CLogReader::Close() {
    if (file_ != nullptr) {
        fclose(file_);
    }
}

bool CLogReader::SetFilter(const char *a_filter) {
    bool result;

    if (a_filter != nullptr) {
        size_t length = strlen(a_filter) + 1;
        filter_ = static_cast<char *>(malloc(length + 1));

        if (filter_ != nullptr) {
            strlcpy(filter_, a_filter, length);
            filter_[length - 1] = '\0';

            result = true;
        } else {
            result = false;
        }
    } else {
        result = false;
    }

    return result;
}

#define LINE_BUFFER_SIZE 4096

char *CLogReader::ReadNextLineDynamic() {
    char *result = nullptr;
    size_t maxlength = 0;

    while (true) {
        char *crlf, *block;

        maxlength += LINE_BUFFER_SIZE;
        if ((result = static_cast<char *>(realloc(result, maxlength + 1))) == nullptr) {
            break;
        }
        block = result + maxlength - LINE_BUFFER_SIZE;

        if (fgets(block, LINE_BUFFER_SIZE + 1, file_) == nullptr) {
            if (block == result) {
                free(result);
                result = nullptr;
            }
            break;
        }

        if (nullptr != (crlf = strchr(block, '\n'))) {
            *crlf = '\0';
            if (crlf != block) {
                if ('\r' == *(--crlf))
                    *crlf = '\0';
            }
            break;
        }
    }

    return result;
}

bool CLogReader::GetNextLine(char *buf, const size_t bufsize) {
    bool result = false;
    char *line = nullptr;

    while ((line = ReadNextLineDynamic()) != nullptr) {
        if (IsStringMatchedFilter(line, filter_)) {
            break;
        }
        free(line);
        line = nullptr;
    }

    if (line != nullptr) {
        size_t lineLength = strlen(line) + 1;
        size_t sizeToWrite = (lineLength <= bufsize) ? lineLength : bufsize;

        strlcpy(buf, line, sizeToWrite);
        result = true;

        free(line);
    }

    return result;
}

extern "C" {

    const char *internalStoragePath;
    const char *filterChar;


    JNIEXPORT jint
    JNICALL
    Java_com_vcoolish_metafilter_MainActivity_filterFromJNI(
            JNIEnv *env,
            jobject instance,
            jstring filter,
            jstring path) {

        internalStoragePath = env->GetStringUTFChars(path, 0);
        filterChar = env->GetStringUTFChars(filter, 0);

        CLogReader mylogreader(internalStoragePath);

        if (mylogreader.Open()) {
            mylogreader.SetFilter(filterChar);
            jclass activityClass = env->GetObjectClass(instance);
            jmethodID messageMeID = env->GetMethodID(
                    activityClass,
                    "messageMe",
                    "(Ljava/lang/String;)V");
            if (messageMeID == 0)
            {
                return -1;
            }
            char buf[4096];
            while (mylogreader.GetNextLine(buf, sizeof(buf))) {
                env->CallVoidMethod(instance, messageMeID, env->NewStringUTF(buf));
            }

            return 0;
        }
        return -1;
    }
}

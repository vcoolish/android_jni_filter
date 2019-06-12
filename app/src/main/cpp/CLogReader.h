#pragma once

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
using namespace std;

class CLogReader final {

public:
    CLogReader() = delete;
    CLogReader(const CLogReader& that) = delete;

    CLogReader(const char *filename);
    ~CLogReader();

    bool Open();
    void Close();

    bool SetFilter(const char *a_filter);

    bool GetNextLine(char *buf, const size_t bufsize);

private:
    const char *filename_;
    FILE *file_;
    bool fileOpened_;

    char *filter_;

    size_t GetNextLineSize(bool seekBack = true);
    char * ReadNextLineDynamic();

    char * ReadNextLine();
};

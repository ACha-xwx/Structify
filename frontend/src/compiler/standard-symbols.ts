export interface StandardSymbol {
  header: string;
  signature: string;
}

// C library declarations shown only when their header is included in the document.
const headers: Record<string, Record<string, string>> = {
  "stdio.h": {
    printf: "int printf(const char *restrict format, ...);",
    fprintf: "int fprintf(FILE *restrict stream, const char *restrict format, ...);",
    sprintf: "int sprintf(char *restrict buffer, const char *restrict format, ...);",
    snprintf: "int snprintf(char *restrict buffer, size_t size, const char *restrict format, ...);",
    scanf: "int scanf(const char *restrict format, ...);",
    fscanf: "int fscanf(FILE *restrict stream, const char *restrict format, ...);",
    sscanf: "int sscanf(const char *restrict buffer, const char *restrict format, ...);",
    puts: "int puts(const char *text);",
    putchar: "int putchar(int character);",
    getchar: "int getchar(void);",
    fgets: "char *fgets(char *restrict buffer, int size, FILE *restrict stream);",
    fputs: "int fputs(const char *restrict text, FILE *restrict stream);",
    fopen: "FILE *fopen(const char *restrict filename, const char *restrict mode);",
    fclose: "int fclose(FILE *stream);",
    fread: "size_t fread(void *restrict buffer, size_t size, size_t count, FILE *restrict stream);",
    fwrite: "size_t fwrite(const void *restrict buffer, size_t size, size_t count, FILE *restrict stream);",
    fflush: "int fflush(FILE *stream);",
    fseek: "int fseek(FILE *stream, long offset, int origin);",
    ftell: "long ftell(FILE *stream);",
    perror: "void perror(const char *text);",
  },
  "stdlib.h": {
    malloc: "void *malloc(size_t size);",
    calloc: "void *calloc(size_t count, size_t size);",
    realloc: "void *realloc(void *buffer, size_t size);",
    free: "void free(void *buffer);",
    atoi: "int atoi(const char *text);",
    atof: "double atof(const char *text);",
    strtol: "long strtol(const char *restrict text, char **restrict end, int base);",
    rand: "int rand(void);",
    srand: "void srand(unsigned int seed);",
    abs: "int abs(int value);",
    exit: "void exit(int status);",
    qsort: "void qsort(void *base, size_t count, size_t size, int (*compare)(const void *, const void *));",
    bsearch: "void *bsearch(const void *key, const void *base, size_t count, size_t size, int (*compare)(const void *, const void *));",
  },
  "string.h": {
    strlen: "size_t strlen(const char *text);",
    strcpy: "char *strcpy(char *restrict destination, const char *restrict source);",
    strncpy: "char *strncpy(char *restrict destination, const char *restrict source, size_t count);",
    strcat: "char *strcat(char *restrict destination, const char *restrict source);",
    strcmp: "int strcmp(const char *left, const char *right);",
    strncmp: "int strncmp(const char *left, const char *right, size_t count);",
    strchr: "char *strchr(const char *text, int character);",
    strstr: "char *strstr(const char *text, const char *substring);",
    memcpy: "void *memcpy(void *restrict destination, const void *restrict source, size_t count);",
    memmove: "void *memmove(void *destination, const void *source, size_t count);",
    memset: "void *memset(void *buffer, int value, size_t count);",
    memcmp: "int memcmp(const void *left, const void *right, size_t count);",
  },
  "math.h": {
    pow: "double pow(double base, double exponent);",
    sqrt: "double sqrt(double value);",
    fabs: "double fabs(double value);",
    ceil: "double ceil(double value);",
    floor: "double floor(double value);",
    sin: "double sin(double angle);",
    cos: "double cos(double angle);",
    tan: "double tan(double angle);",
    log: "double log(double value);",
    exp: "double exp(double value);",
    fmod: "double fmod(double value, double divisor);",
  },
  "ctype.h": Object.fromEntries([
    "isalnum", "isalpha", "isdigit", "islower", "isupper", "isspace", "ispunct", "isprint", "tolower", "toupper",
  ].map((name) => [name, `int ${name}(int character);`])),
  "time.h": {
    clock: "clock_t clock(void);",
    time: "time_t time(time_t *result);",
    difftime: "double difftime(time_t end, time_t beginning);",
    localtime: "struct tm *localtime(const time_t *timer);",
  },
};

export const standardSymbols = new Map<string, StandardSymbol>(
  Object.entries(headers).flatMap(([header, functions]) =>
    Object.entries(functions).map(([name, signature]) => [name, { header, signature }] as const)),
);

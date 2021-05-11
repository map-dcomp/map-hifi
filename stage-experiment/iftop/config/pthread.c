/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
/*
 * pthread.c:
 * Tiny test program to see whether POSIX threads work.
 */

static const char rcsid[] = "$Id$";

#include <sys/types.h>

#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static pthread_mutex_t mtx = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
static int return_value = -1;

void *worker_thread(void *v) {
    /* Record successful return and signal parent to wake up. */
    return_value = 0;
    pthread_mutex_lock(&mtx);
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&mtx);
    while (1) {
        sleep(1);
        pthread_testcancel();
    }
}

/* Start a thread, and have it set a variable to some other value, then signal
 * a condition variable. If this doesn't happen within some set time, we assume
 * that something's gone badly wrong and abort (for instance, the thread never
 * got started). */
int main(void) {
    pthread_t thr;
    int res;
    struct timespec deadline = {0};
    if ((res = pthread_mutex_lock(&mtx)) != 0
        || (res = pthread_create(&thr, NULL, worker_thread, NULL)) != 0) {
        fprintf(stderr, "%s\n", strerror(res));
        return -1;
    }

    /* Thread should now be running; we should wait on the condition
     * variable. */
    do
        deadline.tv_sec = 2 + time(NULL);
    while ((res = pthread_cond_timedwait(&cond, &mtx, &deadline)) == EINTR);
    
    if (res != 0) {
        fprintf(stderr, "%s\n", strerror(res));
        return -1;
    }

    if ((res = pthread_cancel(thr)) != 0
        || (res = pthread_join(thr, NULL)) != 0) {
        fprintf(stderr, "%s\n", strerror(res));
        return -1;
    }
    
    return return_value;
}

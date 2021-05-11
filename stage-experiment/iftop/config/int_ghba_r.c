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
 * int_ghba_r.c:
 * Test program to see whether gethostbyaddr_r takes 8 arguments and returns
 * int.
 */

static const char rcsid[] = "$Id$";

#include <sys/socket.h>
#include <sys/types.h>

#include <errno.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <netinet/in.h>

int main(void) {
    struct in_addr localhost;
    struct hostent hostbuf, *hp;
    char *buf;
    int res, herr;
    size_t buflen = 1024;
    
    localhost.s_addr = htonl(INADDR_LOOPBACK);
    buf = malloc(buflen);
    while ((res = gethostbyaddr_r((char*)&localhost, sizeof localhost, AF_INET,
                                  &hostbuf, buf, buflen, &hp, &herr))
                    == ERANGE)
        buf = (char*)realloc(buf, buflen *= 2);

    /* We assume that the loopback address can always be resolved if
     * gethostbyaddr_r is actually working. */
    if (res || hp == NULL) {
        fprintf(stderr, "errno = %d, herr = %d, res = %d\n", errno, herr, res);
        return -1;
    } else
        return 0;
}

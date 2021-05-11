//BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
// Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
// To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
// the exception of the dcop implementation identified below (see notes).
// 
// Dispersed Computing (DCOMP)
// Mission-oriented Adaptive Placement of Task and Data (MAP) 
// 
// All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// 
// Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
// IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//BBN_LICENSE_END
/*
 * iftop.h:
 *
 */

#ifndef __IFTOP_H_ /* include guard */
#define __IFTOP_H_

#include "config.h"

/* 40 / 2  */
#define HISTORY_LENGTH  20
#define RESOLUTION 2
#define DUMP_RESOLUTION 300

/* At least OpenBSD and NexentaCore do not
 * define s6_addr32 for user land settings.
 */
#if !defined s6_addr32 && defined __sun__
#	define s6_addr32 _S6_un._S6_u32
#elif !defined s6_addr32 && \
		( defined __OpenBSD__ || defined __FreeBSD__ )
#	define s6_addr32 __u6_addr.__u6_addr32
#endif	/* !defined s6_addr32 */

typedef struct {
    long recv[HISTORY_LENGTH];
    long sent[HISTORY_LENGTH];
    double long total_sent;
    double long total_recv;
    int last_write;
} history_type;

void tick(int print);

void *xmalloc(size_t n);
void *xcalloc(size_t n, size_t m);
void *xrealloc(void *w, size_t n);
char *xstrdup(const char *s);
void xfree(void *v);

/* options.c */
void options_read(int argc, char **argv);

struct pfloghdr {
      unsigned char		length;
      unsigned char		af;
      unsigned char		action;
      unsigned char		reason;
      char				ifname[16];
      char				ruleset[16];
      unsigned int		rulenr;
      unsigned int		subrulenr;
      unsigned char		dir;
      unsigned char		pad[3];
};

#endif /* __IFTOP_H_ */

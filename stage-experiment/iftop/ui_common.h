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
 * ui_common.h
 *
 *
 */

#ifndef __UI_COMMON_H_ /* include guard */
#define __UI_COMMON_H_

#include <string.h>
#include <stdio.h>

#include "addr_hash.h"
#include "serv_hash.h"
#include "iftop.h"
#include "resolver.h"
#include "sorted_list.h"
#include "options.h"

#define HISTORY_DIVISIONS 3

#define UNIT_DIVISIONS 4

#define HOSTNAME_LENGTH 256

typedef struct host_pair_line_tag {
  addr_pair ap;
  double long total_recv;
  double long total_sent;
  double long recv[HISTORY_DIVISIONS];
  double long sent[HISTORY_DIVISIONS];
} host_pair_line;

extern options_t options;

sorted_list_type screen_list;
host_pair_line totals;
int peaksent, peakrecv, peaktotal;
extern history_type history_totals;
hash_type* screen_hash;
hash_type* service_hash;

void analyse_data(void);
void screen_list_init(void);
void sprint_host(char * line, int af, struct in6_addr* addr, unsigned int port, unsigned int protocol, int L, int unspecified_as_star);
void readable_size(float, char*, int, int, option_bw_unit_t);

#endif /* __UI_COMMON_H_ */

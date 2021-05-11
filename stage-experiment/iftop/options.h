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
 * options.h:
 *
 */

#ifndef __OPTIONS_H_ /* include guard */
#define __OPTIONS_H_

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>


typedef enum {
  OPTION_PORTS_OFF,
  OPTION_PORTS_SRC,
  OPTION_PORTS_DEST,
  OPTION_PORTS_ON
} option_port_t;

typedef enum {
  OPTION_SORT_DIV1,
  OPTION_SORT_DIV2,
  OPTION_SORT_DIV3,
  OPTION_SORT_SRC,
  OPTION_SORT_DEST
} option_sort_t;

typedef enum {
  OPTION_LINEDISPLAY_TWO_LINE,
  OPTION_LINEDISPLAY_ONE_LINE_BOTH,
  OPTION_LINEDISPLAY_ONE_LINE_RECV,
  OPTION_LINEDISPLAY_ONE_LINE_SENT
} option_linedisplay_t;

typedef enum {
  OPTION_BW_BITS,
  OPTION_BW_BYTES,
  OPTION_BW_PKTS,
} option_bw_unit_t;

/* 
 * This structure has to be defined in the same order as the config 
 * directives in cfgfile.c.  Clearly this is EBW.
 */
typedef struct {
    /* interface on which to listen */
    char *interface;

    int dnsresolution;
    int portresolution;
    /* pcap filter code */
    char *filtercode;

    int showbars;
    option_port_t showports;

    int promiscuous;
    int promiscuous_but_choosy;
    int aggregate_src;
    int aggregate_dest;
    int paused;
    int showhelp;
    int timed_output;
    int no_curses;
    int num_lines;
    option_bw_unit_t bandwidth_unit;
    option_sort_t sort;

    int bar_interval;

    char* screenfilter;
    int freezeorder;

    int screen_offset;

    option_linedisplay_t linedisplay;

    int show_totals;

    long long max_bandwidth;
    int log_scale;

    /* Cross network filter */
    int netfilter;
    struct in_addr netfilternet;
    struct in_addr netfiltermask;

    int netfilter6;
    struct in6_addr netfilter6net;
    struct in6_addr netfilter6mask;

    /* Account for link-local traffic. */
    int link_local;

    char *config_file;
    int config_file_specified;
  
    /* make the output easy to parse */
    int kleen_output;

} options_t;


void options_set_defaults();
void options_read(int argc, char **argv);
void options_read_args(int argc, char **argv);
void options_make();

#endif /* __OPTIONS_H_ */

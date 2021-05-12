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
 * ui_common.c
 *
 *
 */

#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include "addr_hash.h"
#include "serv_hash.h"
#include "iftop.h"
#include "resolver.h"
#include "sorted_list.h"
#include "options.h"

#include "ui_common.h"

/* 2, 10 and 40 seconds */
int history_divs[HISTORY_DIVISIONS] = {1, 5, 20};

#define UNIT_DIVISIONS 4
char* unit_disp[][UNIT_DIVISIONS] = {
  [OPTION_BW_BITS]  = { "b", "Kb", "Mb", "Gb"},
  [OPTION_BW_BYTES] = { "B", "KB", "MB", "GB"},
  [OPTION_BW_PKTS]  = { "p", "Kp", "Mp", "GB"},
};

extern hash_type* history;
extern int history_pos;
extern int history_len;

/*
 * Compare two screen lines based on bandwidth.  Start comparing from the 
 * specified column
 */
int screen_line_bandwidth_compare(host_pair_line* aa, host_pair_line* bb, int start_div) {
    int i;
    switch(options.linedisplay) {
      case OPTION_LINEDISPLAY_ONE_LINE_SENT:
	for(i = start_div; i < HISTORY_DIVISIONS; i++) {
	    if(aa->sent[i] != bb->sent[i]) {
	        return(aa->sent[i] < bb->sent[i]);
	    }
        }
        break;
      case OPTION_LINEDISPLAY_ONE_LINE_RECV:
	for(i = start_div; i < HISTORY_DIVISIONS; i++) {
	    if(aa->recv[i] != bb->recv[i]) {
	        return(aa->recv[i] < bb->recv[i]);
	    }
        }
        break;
      case OPTION_LINEDISPLAY_TWO_LINE:
      case OPTION_LINEDISPLAY_ONE_LINE_BOTH:
        /* fallback to the combined sent+recv that also act as fallback for sent/recv */
	break;
    }
    for(i = start_div; i < HISTORY_DIVISIONS; i++) {
	if(aa->recv[i] + aa->sent[i] != bb->recv[i] + bb->sent[i]) {
	    return(aa->recv[i] + aa->sent[i] < bb->recv[i] + bb->sent[i]);
	}
    }
    return 1;
}

/*
 * Compare two screen lines based on hostname / IP.  Fall over to compare by
 * bandwidth.
 */
int screen_line_host_compare(void* a, void* b, host_pair_line* aa, host_pair_line* bb) {
    char hosta[HOSTNAME_LENGTH], hostb[HOSTNAME_LENGTH];
    int r;

    /* This isn't overly efficient because we resolve again before 
       display. */
    if (options.dnsresolution) {
        resolve(aa->ap.af, a, hosta, HOSTNAME_LENGTH);
        resolve(bb->ap.af, b, hostb, HOSTNAME_LENGTH);
    }
    else {
        inet_ntop(aa->ap.af, a, hosta, sizeof(hosta));
        inet_ntop(bb->ap.af, b, hostb, sizeof(hostb));
    }

    r = strcmp(hosta, hostb);

    if(r == 0) {
        return screen_line_bandwidth_compare(aa, bb, 2);
    }
    else {
        return (r > 0);
    }


}

/*
 * Compare two screen lines based on the sorting options selected.
 */
int screen_line_compare(void* a, void* b) {
    host_pair_line* aa = (host_pair_line*)a;
    host_pair_line* bb = (host_pair_line*)b;
    if(options.sort == OPTION_SORT_DIV1) {
      return screen_line_bandwidth_compare(aa, bb, 0);
    }
    else if(options.sort == OPTION_SORT_DIV2) {
      return screen_line_bandwidth_compare(aa, bb, 1);
    }
    else if(options.sort == OPTION_SORT_DIV3) {
      return screen_line_bandwidth_compare(aa, bb, 2);
    }
    else if(options.sort == OPTION_SORT_SRC) {
      return screen_line_host_compare(&(aa->ap.src6), &(bb->ap.src6), aa, bb);
    }
    else if(options.sort == OPTION_SORT_DEST) {
      return screen_line_host_compare(&(aa->ap.dst6), &(bb->ap.dst6), aa, bb);
    }

    return 1;
}

/*
 * Format a data size in human-readable format
 */
void readable_size(float n, char* buf, int bsize, int ksize,
		   option_bw_unit_t unit) {

    int i = 0;
    float size = 1;

    /* Convert to bits? */
    if (unit == OPTION_BW_BITS) { 
      n *= 8;
    }

    /* Force power of ten for pps */
    if (unit == OPTION_BW_PKTS)
      ksize = 1000;

    while(1) {
      if(n < size * 1000 || i >= UNIT_DIVISIONS - 1) {
        snprintf(buf, bsize, " %4.0f%s", n / size, unit_disp[unit][i]); 
        break;
      }
      i++;
      size *= ksize;
      if(n < size * 10) {
        snprintf(buf, bsize, " %4.2f%s", n / size, unit_disp[unit][i]); 
        break;
      }
      else if(n < size * 100) {
        snprintf(buf, bsize, " %4.1f%s", n / size, unit_disp[unit][i]); 
        break;
      }
  }
}

int history_length(const int d) {
    if (history_len < history_divs[d])
        return history_len * RESOLUTION;
    else
        return history_divs[d] * RESOLUTION;
}

void screen_list_init() {
    screen_list.compare = &screen_line_compare;
    sorted_list_initialise(&screen_list);
}

void screen_list_clear() {
    sorted_list_node* nn = NULL;
    peaksent = peakrecv = peaktotal = 0;
    while((nn = sorted_list_next_item(&screen_list, nn)) != NULL) {
        free(nn->data);
    }
    sorted_list_destroy(&screen_list);
}

/*
 * Calculate peaks and totals
 */
void calculate_totals() {
    int i;

    for(i = 0; i < HISTORY_LENGTH; i++) {
        int j;
        int ii = (HISTORY_LENGTH + history_pos - i) % HISTORY_LENGTH;

        for(j = 0; j < HISTORY_DIVISIONS; j++) {
            if(i < history_divs[j]) {
                totals.recv[j] += history_totals.recv[ii];
                totals.sent[j] += history_totals.sent[ii];
            }
        }

        if(history_totals.recv[i] > peakrecv) {
            peakrecv = history_totals.recv[i];
        }
        if(history_totals.sent[i] > peaksent) {
            peaksent = history_totals.sent[i];
        }
        if(history_totals.recv[i] + history_totals.sent[i] > peaktotal) {
            peaktotal = history_totals.recv[i] + history_totals.sent[i];	
        }
    }
    for(i = 0; i < HISTORY_DIVISIONS; i++) {
      int t = history_length(i);
      totals.recv[i] /= t;
      totals.sent[i] /= t;
    }
}

void make_screen_list() {
    hash_node_type* n = NULL;
    while(hash_next_item(screen_hash, &n) == HASH_STATUS_OK) {
        host_pair_line* line = (host_pair_line*)n->rec;
        int i;
        for(i = 0; i < HISTORY_DIVISIONS; i++) {
          line->recv[i] /= history_length(i);
          line->sent[i] /= history_length(i);
        }

        /* Don't make a new, sorted screen list if order is frozen
         */
        if(!options.freezeorder) {
            sorted_list_insert(&screen_list, line);
        } 
	 
    }
}

/*
 * Zeros all data in the screen hash, but does not remove items.
 */
void screen_hash_clear() {
    hash_node_type* n = NULL;
    while(hash_next_item(screen_hash, &n) == HASH_STATUS_OK) {
        host_pair_line* hpl = (host_pair_line*)n->rec;
        hpl->total_recv = hpl->total_sent = 0;
        memset(hpl->recv, 0, sizeof(hpl->recv));
        memset(hpl->sent, 0, sizeof(hpl->sent));
    }
}

void analyse_data() {
    hash_node_type* n = NULL;

    if(options.paused == 1) {
      return;
    }

    // Zero totals
    memset(&totals, 0, sizeof totals);

    if(options.freezeorder) {
      screen_hash_clear();
    }
    else {
      screen_list_clear();
      hash_delete_all(screen_hash);
    }

    while(hash_next_item(history, &n) == HASH_STATUS_OK) {
        history_type* d = (history_type*)n->rec;
        host_pair_line* screen_line;
	union {
	    host_pair_line **h_p_l_pp;
	    void **void_pp;
	} u_screen_line = { &screen_line };
        addr_pair ap;
        int i;

        ap = *(addr_pair*)n->key;

        /* Aggregate hosts, if required */
        if(options.aggregate_src) {
            memset(&ap.src6, '\0', sizeof(ap.src6));
        }
        if(options.aggregate_dest) {
            memset(&ap.dst6, '\0', sizeof(ap.dst6));
        }

        /* Aggregate ports, if required */
        if(options.showports == OPTION_PORTS_DEST || options.showports == OPTION_PORTS_OFF) {
            ap.src_port = 0;
        }
        if(options.showports == OPTION_PORTS_SRC || options.showports == OPTION_PORTS_OFF) {
            ap.dst_port = 0;
        }
        if(options.showports == OPTION_PORTS_OFF) {
            ap.protocol = 0;
        }

	
        if(hash_find(screen_hash, &ap, u_screen_line.void_pp) == HASH_STATUS_KEY_NOT_FOUND) {
            screen_line = xcalloc(1, sizeof *screen_line);
            hash_insert(screen_hash, &ap, screen_line);
            screen_line->ap = ap;
        }
        
	screen_line->total_sent += d->total_sent;
	screen_line->total_recv += d->total_recv;

        for(i = 0; i < HISTORY_LENGTH; i++) {
            int j;
            int ii = (HISTORY_LENGTH + history_pos - i) % HISTORY_LENGTH;

            for(j = 0; j < HISTORY_DIVISIONS; j++) {
                if(i < history_divs[j]) {
                    screen_line->recv[j] += d->recv[ii];
                    screen_line->sent[j] += d->sent[ii];
                }
            }
        }

    }

    make_screen_list();

    
    calculate_totals();

}

void sprint_host(char * line, int af, struct in6_addr* addr, unsigned int port, unsigned int protocol, int L, int unspecified_as_star) {
    char hostname[HOSTNAME_LENGTH];
    char service[HOSTNAME_LENGTH];
    char* s_name;
    union {
        char **ch_pp;
        void **void_pp;
    } u_s_name = { &s_name };

    ip_service skey;
    int left;

    if(IN6_IS_ADDR_UNSPECIFIED(addr) && unspecified_as_star) {
        sprintf(hostname, " * ");
    }
    else {
        if (options.dnsresolution)
            resolve(af, addr, hostname, L);
        else
            inet_ntop(af, addr, hostname, sizeof(hostname));
    }
    left = strlen(hostname);

    if(port != 0) {
      skey.port = port;
      skey.protocol = protocol;
      if(options.portresolution && hash_find(service_hash, &skey, u_s_name.void_pp) == HASH_STATUS_OK) {
        snprintf(service, HOSTNAME_LENGTH, ":%s", s_name);
      }
      else {
        snprintf(service, HOSTNAME_LENGTH, ":%d", port);
      }
    }
    else {
      service[0] = '\0';
    }
    
    /* If we're showing IPv6 addresses with a port number, put them in square
     * brackets. */
    if(port == 0 || af == AF_INET || L < 2) {
      sprintf(line, "%-*s", L, hostname);
    }
    else {
      sprintf(line, "[%-.*s]", L-2, hostname);
      left += 2;
    }
    if(left > (L - strlen(service))) {
        left = L - strlen(service);
        if(left < 0) {
           left = 0;
        }
    }
    sprintf(line + left, "%-*s", L-left, service);
}


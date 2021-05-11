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
/* hash table */

#include <stdio.h>
#include <stdlib.h>
#include <netdb.h>
#include "serv_hash.h"
#include "hash.h"
#include "iftop.h"

// Deliberately not a power of 2 or 10
#define hash_table_size 123

int serv_hash_compare(void* a, void* b) {
    ip_service* aa = (ip_service*)a;
    ip_service* bb = (ip_service*)b;
    return (aa->port == bb->port &&
            aa->protocol == bb->protocol);
}

int serv_hash_hash(void* key) {
    ip_service* serv = (ip_service*)key;
    return serv->protocol % hash_table_size;
}

void* serv_hash_copy_key(void* orig) {
    ip_service* copy;
    copy = xmalloc(sizeof *copy);
    *copy = *(ip_service*)orig;
    return copy;
}

void serv_hash_delete_key(void* key) {
    free(key);
}

/*
 * Allocate and return a hash
 */
hash_type* serv_hash_create() {
    hash_type* hash_table;
    hash_table = xcalloc(hash_table_size, sizeof *hash_table);
    hash_table->size = hash_table_size;
    hash_table->compare = &serv_hash_compare;
    hash_table->hash = &serv_hash_hash;
    hash_table->delete_key = &serv_hash_delete_key;
    hash_table->copy_key = &serv_hash_copy_key;
    hash_initialise(hash_table);
    return hash_table;
}

void serv_hash_initialise(hash_type* sh) {
  struct servent* ent;
  struct protoent* pent;
  ip_service* service;
  setprotoent(1);
  while((ent = getservent()) != NULL) {
    pent = getprotobyname(ent->s_proto);
    if(pent != NULL) {
      service = xmalloc(sizeof(ip_service));
      service->port = ntohs(ent->s_port);
      service->protocol = pent->p_proto;
      hash_insert(sh, service, xstrdup(ent->s_name));
    }
  }
  endprotoent();
}

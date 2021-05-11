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
#include <netinet/in.h>
#include "counter_hash.h"
#include "hash.h"
#include "iftop.h"

#define hash_table_size 256

int counter_hash_compare(void* a, void* b) {
    return *(long*)a == *(long*)b;
}

int counter_hash_hash(void* key) {
    int hash;
    long addr;
        
    addr = *(long*)key;

    hash = ((addr & 0x000000FF)
            + (addr & 0x0000FF00 >> 8)
            + (addr & 0x00FF0000 >> 16)
            + (addr & 0xFF000000 >> 24)
           ) % 0xFF;

    return hash;
}

void* counter_hash_copy_key(void* orig) {
    struct in_addr* copy;
    copy = xmalloc(sizeof *copy);
    *copy = *(struct in_addr*)orig;
    return copy;
}

void counter_hash_delete_key(void* key) {
    free(key);
}

/*
 * Allocate and return a hash
 */
hash_type* counter_hash_create() {
    hash_type* hash_table;
    hash_table = xcalloc(hash_table_size, sizeof *hash_table);
    hash_table->size = hash_table_size;
    hash_table->compare = &counter_hash_compare;
    hash_table->hash = &counter_hash_hash;
    hash_table->delete_key = &counter_hash_delete_key;
    hash_table->copy_key = &counter_hash_copy_key;
    hash_initialise(hash_table);
    return hash_table;
}


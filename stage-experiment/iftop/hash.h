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
 * addr_hash.h:
 *
 */

#ifndef __HASH_H_ /* include guard */
#define __HASH_H_

/* implementation independent declarations */
typedef enum {
    HASH_STATUS_OK,
    HASH_STATUS_MEM_EXHAUSTED,
    HASH_STATUS_KEY_NOT_FOUND
} hash_status_enum;

typedef struct node_tag {
    struct node_tag *next;       /* next node */
    void* key;                /* key */
    void* rec;                /* user data */
} hash_node_type;

typedef struct {
    int (*compare) (void*, void*);
    int (*hash) (void*);
    void* (*copy_key) (void*);
    void (*delete_key) (void*);
    hash_node_type** table;
    int size;
} hash_type;


hash_status_enum hash_initialise(hash_type*);
hash_status_enum hash_destroy(hash_type*);
hash_status_enum hash_insert(hash_type*, void* key, void *rec);
hash_status_enum hash_delete(hash_type* hash_table, void* key);
hash_status_enum hash_find(hash_type* hash_table, void* key, void** rec);
hash_status_enum hash_next_item(hash_type* hash_table, hash_node_type** ppnode);
void hash_delete_all(hash_type* hash_table);

#endif /* __HASH_H_ */

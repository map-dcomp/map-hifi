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
 * sorted_list.h:
 *
 */

#ifndef __SORTED_LIST_H_ /* include guard */
#define __SORTED_LIST_H_

typedef struct sorted_list_node_tag {
    struct sorted_list_node_tag* next;
    void* data;
} sorted_list_node;

typedef struct {
    sorted_list_node root;
    int (*compare)(void*, void*);
} sorted_list_type;

void sorted_list_initialise(sorted_list_type* list);
void sorted_list_insert(sorted_list_type* list, void* item);
sorted_list_node* sorted_list_next_item(sorted_list_type* list, sorted_list_node* prev);
void sorted_list_destroy(sorted_list_type* list);


#endif /* __SORTED_LIST_H_ */

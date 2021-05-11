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
 * stringmap.c: sucky implementation of binary trees
 *
 * This makes no attempt to balance the tree, so has bad worst-case behaviour.
 * Also, I haven't implemented removal of items from the tree. So sue me.
 *
 * Copyright (c) 2001 Chris Lightfoot. All rights reserved.
 *
 */

static const char rcsid[] = "$Id$";


#include <stdlib.h>
#include <string.h>

#include "stringmap.h"
#include "vector.h"
#include "iftop.h"

/* stringmap_new:
 * Allocate memory for a new stringmap. */
stringmap stringmap_new() {
    stringmap S;
    
    S = xcalloc(sizeof *S, 1);

    return S;
}

/* stringmap_delete:
 * Free memory for a stringmap. */
void stringmap_delete(stringmap S) {
    if (!S) return;
    if (S->l) stringmap_delete(S->l);
    if (S->g) stringmap_delete(S->g);

    xfree(S->key);
    xfree(S);
}

/* stringmap_delete_free:
 * Free memory for a stringmap, and the objects contained in it, assuming that
 * they are pointers to memory allocated by xmalloc(3). */
void stringmap_delete_free(stringmap S) {
    if (!S) return;
    if (S->l) stringmap_delete_free(S->l);
    if (S->g) stringmap_delete_free(S->g);

    xfree(S->key);
    xfree(S->d.v);
    xfree(S);
}

/* stringmap_insert:
 * Insert into S an item having key k and value d. Returns a pointer to
 * the existing item value, or NULL if a new item was created. 
 */
item *stringmap_insert(stringmap S, const char *k, const item d) {
    if (!S) return NULL;
    if (S->key == NULL) {
        S->key = xstrdup(k);
        S->d   = d;
        return NULL;
    } else {
        stringmap S2;
        for (S2 = S;;) {
            int i = strcmp(k, S2->key);
            if (i == 0) {
                return &(S2->d);
            }
            else if (i < 0) {
                if (S2->l) S2 = S2->l;
                else {
                    if (!(S2->l = stringmap_new())) return NULL;
                    S2->l->key = xstrdup(k);
                    S2->l->d   = d;
                    return NULL;
                }
            } else if (i > 0) {
                if (S2->g) S2 = S2->g;
                else {
                    if (!(S2->g = stringmap_new())) return NULL;
                    S2->g->key = xstrdup(k);
                    S2->g->d   = d;
                    return NULL;
                }
            }
        }
    }
}

/* stringmap_find:
 * Find in d an item having key k in the stringmap S, returning the item found
 * on success NULL if no key was found. */
stringmap stringmap_find(const stringmap S, const char *k) {
    stringmap S2;
    int i;
    if (!S || S->key == NULL) return 0;
    for (S2 = S;;) {
        i = strcmp(k, S2->key);
        if (i == 0) return S2;
        else if (i < 0) {
            if (S2->l) S2 = S2->l;
            else return NULL;
        } else if (i > 0) {
            if (S2->g) S2 = S2->g;
            else return NULL;
        }
    }
}

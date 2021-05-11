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
 * vector.c:
 * simple vectors
 *
 * Copyright (c) 2001 Chris Lightfoot. All rights reserved.
 *
 */

static const char rcsid[] = "$Id$";

#ifdef HAVE_CONFIG_H
/*include "configuration.h"*/
#endif /* HAVE_CONFIG_H */

#include <stdlib.h>
#include <string.h>

#include "vector.h"
#include "iftop.h"

vector vector_new(void) {
    vector v;
    
    v = xcalloc(1, sizeof *v);
    if (!v) return NULL;

    v->ary = xcalloc(16, sizeof *v->ary);
    v->n = 16;
    v->n_used = 0;
    return v;
}

void vector_delete(vector v) {
    xfree(v->ary);
    xfree(v);
}

void vector_delete_free(vector v) {
    item *i;
    vector_iterate(v, i) {
        xfree(i->v);
    }
    xfree(v->ary);
    xfree(v);
}

void vector_push_back(vector v, const item t) {
    if (v->n_used == v->n) vector_reallocate(v, v->n * 2);
    v->ary[v->n_used++] = t;
}

void vector_pop_back(vector v) {
    if (v->n_used > 0) {
        --v->n_used;
        if (v->n_used < v->n / 2) vector_reallocate(v, v->n / 2);
    }
}

item vector_back(vector v) {
    return v->ary[v->n_used - 1];
}

item *vector_remove(vector v, item *t) {
    if (t >= v->ary + v->n_used) return NULL;
    if (t < v->ary + v->n_used - 1)
        memmove(t, t + 1, (v->n_used - (t - v->ary)) * sizeof(item));
    memset(v->ary + v->n_used--, 0, sizeof(item));
    if (v->n_used < v->n / 2 && v->n > 16) {
        size_t i = t - v->ary;
        vector_reallocate(v, v->n / 2);
        t = v->ary + i;
    }
    return t;
}

void vector_reallocate(vector v, const size_t n) {
    if (n < v->n_used || n <= 0) return;
    v->ary = xrealloc(v->ary, n * sizeof(item));
    memset(v->ary + v->n_used, 0, (v->n - v->n_used) * sizeof(item));
    v->n = n;
}

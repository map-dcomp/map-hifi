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
 * screenfilter.c:
 *
 * Copyright (c) 2002 DecisionSoft Ltd.
 * Paul Warren (pdw) Fri Oct 25 10:21:00 2002
 *
 */

#include "config.h"

#ifdef HAVE_REGCOMP

#include <sys/types.h>
#include <regex.h>
#include <stdio.h>
#include "iftop.h"
#include "options.h"

static const char rcsid[] = "$Id$";

extern options_t options ;

regex_t preg;

int screen_filter_set(char* s) {
    int r;

    if(options.screenfilter != NULL) {
        xfree(options.screenfilter);
        options.screenfilter = NULL;
        regfree(&preg);
    }

    r = regcomp(&preg, s, REG_ICASE|REG_EXTENDED);
      
    if(r == 0) {
        options.screenfilter = s;
        return 1;
    }
    else {
        xfree(s);
        return 0;
    }
}

int screen_filter_match(char *s) {
    int r;
    if(options.screenfilter == NULL) {
        return 1;
    }

    r = regexec(&preg, s, 0, NULL, 0);
    if(r == 0) {
        return 1;
    }
    else {
        return 0;
    }
}

#endif /* HAVE_REGCOMP */

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
 * Copyright (c) 1992, 1993, 1994, 1995, 1996
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code distributions
 * retain the above copyright notice and this paragraph in its entirety, (2)
 * distributions including binary code include the above copyright notice and
 * this paragraph in its entirety in the documentation or other materials
 * provided with the distribution, and (3) all advertising materials mentioning
 * features or use of this software display the following acknowledgement:
 * ``This product includes software developed by the University of California,
 * Lawrence Berkeley Laboratory and its contributors.'' Neither the name of
 * the University nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior
 * written permission.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 * @(#) $Header$ (LBL)
 */

/* Network to host order macros */

#ifdef LBL_ALIGN
#define EXTRACT_16BITS(p) \
	((u_int16_t)*((const u_int8_t *)(p) + 0) << 8 | \
	(u_int16_t)*((const u_int8_t *)(p) + 1))
#define EXTRACT_32BITS(p) \
	((u_int32_t)*((const u_int8_t *)(p) + 0) << 24 | \
	(u_int32_t)*((const u_int8_t *)(p) + 1) << 16 | \
	(u_int32_t)*((const u_int8_t *)(p) + 2) << 8 | \
	(u_int32_t)*((const u_int8_t *)(p) + 3))
#else
#define EXTRACT_16BITS(p) \
	((u_int16_t)ntohs(*(const u_int16_t *)(p)))
#define EXTRACT_32BITS(p) \
	((u_int32_t)ntohl(*(const u_int32_t *)(p)))
#endif

#define EXTRACT_24BITS(p) \
	((u_int32_t)*((const u_int8_t *)(p) + 0) << 16 | \
	(u_int32_t)*((const u_int8_t *)(p) + 1) << 8 | \
	(u_int32_t)*((const u_int8_t *)(p) + 2))

/* Little endian protocol host order macros */

#define EXTRACT_LE_8BITS(p) (*(p))
#define EXTRACT_LE_16BITS(p) \
	((u_int16_t)*((const u_int8_t *)(p) + 1) << 8 | \
	(u_int16_t)*((const u_int8_t *)(p) + 0))
#define EXTRACT_LE_32BITS(p) \
	((u_int32_t)*((const u_int8_t *)(p) + 3) << 24 | \
	(u_int32_t)*((const u_int8_t *)(p) + 2) << 16 | \
	(u_int32_t)*((const u_int8_t *)(p) + 1) << 8 | \
	(u_int32_t)*((const u_int8_t *)(p) + 0))

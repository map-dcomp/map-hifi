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
/* @(#) $Header$ (LBL) */
/*
 * Point to Point Protocol (PPP) RFC1331
 *
 * Copyright 1989 by Carnegie Mellon.
 *
 * Permission to use, copy, modify, and distribute this program for any
 * purpose and without fee is hereby granted, provided that this copyright
 * and permission notice appear on all copies and supporting documentation,
 * the name of Carnegie Mellon not be used in advertising or publicity
 * pertaining to distribution of the program without specific prior
 * permission, and notice be given in supporting documentation that copying
 * and distribution is by permission of Carnegie Mellon and Stanford
 * University.  Carnegie Mellon makes no representations about the
 * suitability of this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */
#define PPP_HDRLEN	4	/* length of PPP header */

#define PPP_ADDRESS	0xff	/* The address byte value */
#define PPP_CONTROL	0x03	/* The control byte value */

/* Protocol numbers */
#define PPP_IP		0x0021	/* Raw IP */
#define PPP_OSI		0x0023	/* OSI Network Layer */
#define PPP_NS		0x0025	/* Xerox NS IDP */
#define PPP_DECNET	0x0027	/* DECnet Phase IV */
#define PPP_APPLE	0x0029	/* Appletalk */
#define PPP_IPX		0x002b	/* Novell IPX */
#define PPP_VJC		0x002d	/* Van Jacobson Compressed TCP/IP */
#define PPP_VJNC	0x002f	/* Van Jacobson Uncompressed TCP/IP */
#define PPP_BRPDU	0x0031	/* Bridging PDU */
#define PPP_STII	0x0033	/* Stream Protocol (ST-II) */
#define PPP_VINES	0x0035	/* Banyan Vines */
#define PPP_IPV6	0x0057	/* IPv6 */
#define	PPP_COMP	0x00fd	/* Compressed Datagram */

#define PPP_HELLO	0x0201	/* 802.1d Hello Packets */
#define PPP_LUXCOM	0x0231	/* Luxcom */
#define PPP_SNS		0x0233	/* Sigma Network Systems */

#define PPP_IPCP	0x8021	/* IP Control Protocol */
#define PPP_OSICP	0x8023	/* OSI Network Layer Control Protocol */
#define PPP_NSCP	0x8025	/* Xerox NS IDP Control Protocol */
#define PPP_DECNETCP	0x8027	/* DECnet Control Protocol */
#define PPP_APPLECP	0x8029	/* Appletalk Control Protocol */
#define PPP_IPXCP	0x802b	/* Novell IPX Control Protocol */
#define PPP_STIICP	0x8033	/* Strean Protocol Control Protocol */
#define PPP_VINESCP	0x8035	/* Banyan Vines Control Protocol */
#define PPP_IPV6CP	0x8057	/* IPv6 Control Protocol */
#define PPP_CCP		0x80fd	/* Compress Control Protocol */

#define PPP_LCP		0xc021	/* Link Control Protocol */
#define PPP_PAP		0xc023	/* Password Authentication Protocol */
#define PPP_LQM		0xc025	/* Link Quality Monitoring */
#define PPP_CHAP	0xc223	/* Challenge Handshake Authentication Protocol */
#define PPP_BACP	0xc02b	/* Bandwidth Allocation Control Protocol */
#define PPP_BAP		0xc02d	/* BAP */
#define PPP_MP		0xc03d	/* Multi-Link */

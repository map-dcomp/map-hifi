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
 * kui.c:
 *
 * Based on tui.c.
 *
 * This user interface does not make use of curses. Instead, it prints its
 * output to STDOUT. This output is activated by providing the '-k' flag.
 * The output is meant to be easy to parse by another program.
 *
 */

#include "config.h"

#include <string.h>
#include <stdio.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>

#if defined(HAVE_TERMIOS_H)
#include <termios.h>
#elif defined(HAVE_SGTTY_H) && defined(TIOCSETP)
#include <sys/ioctl.h>
#include <sgtty.h>
#elif defined(HAVE_TERMIO_H)
#include <sys/ioctl.h>
#include <termio.h>
#else
#include <stdlib.h>
#endif

#include "sorted_list.h"
#include "options.h"
#include "ui_common.h"

/* Width of the host column in the output */
#define PRINT_WIDTH 40

/*
 * UI print function
 */
void kui_print() {
  sorted_list_node* nn = NULL;
  char host1[HOSTNAME_LENGTH], host2[HOSTNAME_LENGTH];
  int j;
  float sent, received;

  /* Traverse the list of all connections */
  while((nn = sorted_list_next_item(&screen_list, nn)) != NULL) {
    /* Get the connection information */
    host_pair_line* screen_line = (host_pair_line*)nn->data;

    /* convert host names to strings */
    inet_ntop(screen_line->ap.af, &(screen_line->ap.src6), host1, sizeof(host1));
    inet_ntop(screen_line->ap.af, &(screen_line->ap.dst6), host2, sizeof(host2));

    sent = screen_line->sent[0];
    received = screen_line->recv[0];
    if (options.bandwidth_unit == OPTION_BW_BITS) { 
      sent *= 8;
      received *= 8;
    }

    if(sent > 0 || received > 0) {
        /* host1 ; host1 port ; host2 ; host2 port ; sent bits ; received bits */
        printf("%s;%d;%s;%d;%.0f;%.0f\n", host1, screen_line->ap.src_port,
               host2, screen_line->ap.dst_port,
               sent, received);
    }
  }

  /* Double divider line */
  for (j = 0; j < PRINT_WIDTH + 52; j++) {
    printf("=");
  }
  printf("\n\n");
}


/*
 * Text interface data structure initializations.
 */
void kui_init() {
  screen_list_init();
  screen_hash = addr_hash_create();
  service_hash = serv_hash_create();
  serv_hash_initialise(service_hash);
}


/*
 * Tick function indicating screen refresh
 */
void kui_tick(int print) {
  if (print) {
    kui_print();
  }
}


/*
 * Main UI loop. Code any interactive character inputs here.
 */
void kui_loop() {
  int i;
  extern sig_atomic_t foad;

#if defined(HAVE_TERMIOS_H)
  struct termios new_termios, old_termios;

  tcgetattr(STDIN_FILENO, &old_termios);
  new_termios = old_termios;
  new_termios.c_lflag &= ~(ICANON|ECHO);
  new_termios.c_cc[VMIN] = 1;
  new_termios.c_cc[VTIME] = 0;
  tcsetattr(STDIN_FILENO, TCSANOW, &new_termios);
#elif defined(HAVE_SGTTY_H) && defined(TIOCSETP)
  struct sgttyb new_tty, old_tty;

  ioctl(STDIN_FILENO, TIOCGETP, &old_tty);
  new_tty = old_tty;
  new_tty.sg_flags &= ~(ICANON|ECHO);
  ioctl(STDIN_FILENO, TIOCSETP, &new_tty);
#elif defined(HAVE_TERMIO_H)
  struct termio new_termio, old_termio;

  ioctl(0, TCGETA, &old_termio);
  new_termio = old_termio;
  new_termio.c_lflag &= ~(ICANON|ECHO);
  new_termio.c_cc[VMIN] = 1;
  new_termio.c_cc[VTIME] = 0;
  ioctl(0, TCSETA, &new_termio);
#else
  system("/bin/stty cbreak -echo >/dev/null 2>&1");
#endif

  while ((i = getchar()) != 'q' && foad == 0) {
    switch (i) {
      case 'u':
        tick(1);
        break;
      default:
        break;
    }
  }

#if defined(HAVE_TERMIOS_H)
  tcsetattr(STDIN_FILENO, TCSANOW, &old_termios);
#elif defined(HAVE_SGTTY_H) && defined(TIOCSETP)
  ioctl(0, TIOCSETP, &old_tty);
#elif defined(HAVE_TERMIO_H)
  ioctl(0, TCSETA, &old_termio);
#else
  system("/bin/stty -cbreak echo >/dev/null 2>&1");
#endif
}


#!/usr/bin/env python3
from scapy.all import Ether, IP, UDP, Raw, sendp, conf
import argparse, os, sys, signal

def sigint(_sig, _frm): print("\n[+] exiting"); sys.exit(0)
signal.signal(signal.SIGINT, sigint)

ap = argparse.ArgumentParser(
    description="Minimal IP+MAC spoofer (UDP) – edu / lab use only")
ap.add_argument("-i", "--iface", required=True,
                help="network interface to transmit on (e.g. eth0)")
ap.add_argument("-d", "--dst-ip", required=True,
                help="destination IP address")
ap.add_argument("-m", "--dst-mac", default="ff:ff:ff:ff:ff:ff",
                help="destination MAC (default = broadcast)")
ap.add_argument("-s", "--src-ip", required=True,
                help="FORGED source IP address")
ap.add_argument("-c", "--src-mac", required=True,
                help="FORGED source MAC address (must differ from iface MAC)")
ap.add_argument("-p", "--payload", default="spoof-test",
                help="UDP payload text")
ap.add_argument("-n", "--count", type=int, default=1,
                help="number of packets to send (default 1)")
ap.add_argument("--sport", type=int, default=55555,
                help="UDP source port")
ap.add_argument("--dport", type=int, default=55555,
                help="UDP dest port")
args = ap.parse_args()

if os.geteuid() != 0:
    print("[-] run with sudo or as root"); sys.exit(1)

conf.iface = args.iface
pkt = (Ether(src=args.src_mac, dst=args.dst_mac) /
       IP(src=args.src_ip, dst=args.dst_ip) /
       UDP(sport=args.sport, dport=args.dport) /
       Raw(load=args.payload.encode()))
print(f"[+] sending {args.count} spoofed packet(s) on {args.iface}")
sendp(pkt, count=args.count, inter=0, verbose=False)
print("[+] done")

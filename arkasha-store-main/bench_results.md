# KV Formats Benchmark

## A.Tiny-ASCII | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0833 | 555.6 | 196.1 |
| Custom(TLV) | 0.0916 | 909.1 | 1000.0 |
| Protobuf(Struct) | 0.1524 | 192.3 | 250.0 |

**Best size:** Avro(Generic) (0,08 KB/rec) — **Best enc:** Custom(TLV) (909,1 Krec/s) — **Best dec:** Custom(TLV) (1000,0 Krec/s)

## A.Tiny-ASCII | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0833 | 2272.7 | 775.2 |
| Custom(TLV) | 0.0916 | 3030.3 | 2857.1 |
| Protobuf(Struct) | 0.1524 | 657.9 | 819.7 |

**Best size:** Avro(Generic) (0,08 KB/rec) — **Best enc:** Custom(TLV) (3030,3 Krec/s) — **Best dec:** Custom(TLV) (2857,1 Krec/s)

## A.Tiny-ASCII | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0833 | 2293.6 | 1577.3 |
| Custom(TLV) | 0.0916 | 6410.3 | 6097.6 |
| Protobuf(Struct) | 0.1523 | 1066.1 | 865.1 |

**Best size:** Avro(Generic) (0,08 KB/rec) — **Best enc:** Custom(TLV) (6410,3 Krec/s) — **Best dec:** Custom(TLV) (6097,6 Krec/s)

## B.Long-Strings | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.3405 | 1428.6 | 434.8 |
| Custom(TLV) | 0.3476 | 2500.0 | 2000.0 |
| Protobuf(Struct) | 0.4228 | 238.1 | 476.2 |

**Best size:** Avro(Generic) (0,34 KB/rec) — **Best enc:** Custom(TLV) (2500,0 Krec/s) — **Best dec:** Custom(TLV) (2000,0 Krec/s)

## B.Long-Strings | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.3407 | 885.0 | 900.9 |
| Custom(TLV) | 0.3478 | 1612.9 | 2500.0 |
| Protobuf(Struct) | 0.4230 | 458.7 | 454.5 |

**Best size:** Avro(Generic) (0,34 KB/rec) — **Best enc:** Custom(TLV) (1612,9 Krec/s) — **Best dec:** Custom(TLV) (2500,0 Krec/s)

## B.Long-Strings | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.3403 | 939.8 | 817.0 |
| Custom(TLV) | 0.3474 | 1960.8 | 2304.1 |
| Protobuf(Struct) | 0.4226 | 513.3 | 697.4 |

**Best size:** Avro(Generic) (0,34 KB/rec) — **Best enc:** Custom(TLV) (1960,8 Krec/s) — **Best dec:** Custom(TLV) (2304,1 Krec/s)

## C.Many-Nulls | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0319 | 1428.6 | 1428.6 |
| Custom(TLV) | 0.0402 | 3333.3 | 5000.0 |
| Protobuf(Struct) | 0.1031 | 714.3 | 909.1 |

**Best size:** Avro(Generic) (0,03 KB/rec) — **Best enc:** Custom(TLV) (3333,3 Krec/s) — **Best dec:** Custom(TLV) (5000,0 Krec/s)

## C.Many-Nulls | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0319 | 2857.1 | 1724.1 |
| Custom(TLV) | 0.0402 | 9090.9 | 14285.7 |
| Protobuf(Struct) | 0.1030 | 892.9 | 980.4 |

**Best size:** Avro(Generic) (0,03 KB/rec) — **Best enc:** Custom(TLV) (9090,9 Krec/s) — **Best dec:** Custom(TLV) (14285,7 Krec/s)

## C.Many-Nulls | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0320 | 2762.4 | 1845.0 |
| Custom(TLV) | 0.0403 | 7575.8 | 13888.9 |
| Protobuf(Struct) | 0.1031 | 1288.7 | 1103.8 |

**Best size:** Avro(Generic) (0,03 KB/rec) — **Best enc:** Custom(TLV) (7575,8 Krec/s) — **Best dec:** Custom(TLV) (13888,9 Krec/s)

## D.Heavy-Lists | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.2549 | 833.3 | 322.6 |
| Custom(TLV) | 0.2627 | 833.3 | 909.1 |
| Protobuf(Struct) | 0.4055 | 400.0 | 434.8 |

**Best size:** Avro(Generic) (0,25 KB/rec) — **Best enc:** Avro(Generic) (833,3 Krec/s) — **Best dec:** Custom(TLV) (909,1 Krec/s)

## D.Heavy-Lists | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.2547 | 534.8 | 230.9 |
| Custom(TLV) | 0.2625 | 847.5 | 961.5 |
| Protobuf(Struct) | 0.4053 | 340.1 | 421.9 |

**Best size:** Avro(Generic) (0,25 KB/rec) — **Best enc:** Custom(TLV) (847,5 Krec/s) — **Best dec:** Custom(TLV) (961,5 Krec/s)

## D.Heavy-Lists | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.2550 | 782.5 | 315.9 |
| Custom(TLV) | 0.2628 | 838.9 | 965.3 |
| Protobuf(Struct) | 0.4057 | 335.6 | 410.2 |

**Best size:** Avro(Generic) (0,26 KB/rec) — **Best enc:** Custom(TLV) (838,9 Krec/s) — **Best dec:** Custom(TLV) (965,3 Krec/s)

## E.Unicode/Emoji | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.1407 | 1666.7 | 833.3 |
| Custom(TLV) | 0.1485 | 1666.7 | 2500.0 |
| Protobuf(Struct) | 0.2181 | 138.9 | 714.3 |

**Best size:** Avro(Generic) (0,14 KB/rec) — **Best enc:** Avro(Generic) (1666,7 Krec/s) — **Best dec:** Custom(TLV) (2500,0 Krec/s)

## E.Unicode/Emoji | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.1406 | 1234.6 | 869.6 |
| Custom(TLV) | 0.1485 | 1538.5 | 2173.9 |
| Protobuf(Struct) | 0.2180 | 645.2 | 699.3 |

**Best size:** Avro(Generic) (0,14 KB/rec) — **Best enc:** Custom(TLV) (1538,5 Krec/s) — **Best dec:** Custom(TLV) (2173,9 Krec/s)

## E.Unicode/Emoji | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.1406 | 1184.8 | 836.1 |
| Custom(TLV) | 0.1484 | 1538.5 | 2336.4 |
| Protobuf(Struct) | 0.2180 | 798.7 | 646.0 |

**Best size:** Avro(Generic) (0,14 KB/rec) — **Best enc:** Custom(TLV) (1538,5 Krec/s) — **Best dec:** Custom(TLV) (2336,4 Krec/s)

## F.High-Duplication | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0956 | 1666.7 | 1111.1 |
| Custom(TLV) | 0.1034 | 3333.3 | 3333.3 |
| Protobuf(Struct) | 0.1730 | 833.3 | 833.3 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (3333,3 Krec/s) — **Best dec:** Custom(TLV) (3333,3 Krec/s)

## F.High-Duplication | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0953 | 1694.9 | 1010.1 |
| Custom(TLV) | 0.1032 | 3125.0 | 3225.8 |
| Protobuf(Struct) | 0.1727 | 826.4 | 862.1 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (3125,0 Krec/s) — **Best dec:** Custom(TLV) (3225,8 Krec/s)

## F.High-Duplication | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0953 | 1666.7 | 910.7 |
| Custom(TLV) | 0.1032 | 3144.7 | 3623.2 |
| Protobuf(Struct) | 0.1727 | 774.0 | 782.5 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (3144,7 Krec/s) — **Best dec:** Custom(TLV) (3623,2 Krec/s)

## G.Skewed-Ages | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0868 | 2000.0 | 1250.0 |
| Custom(TLV) | 0.0949 | 5000.0 | 10000.0 |
| Protobuf(Struct) | 0.1573 | 1000.0 | 1000.0 |

**Best size:** Avro(Generic) (0,09 KB/rec) — **Best enc:** Custom(TLV) (5000,0 Krec/s) — **Best dec:** Custom(TLV) (10000,0 Krec/s)

## G.Skewed-Ages | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0867 | 735.3 | 1098.9 |
| Custom(TLV) | 0.0949 | 4761.9 | 7142.9 |
| Protobuf(Struct) | 0.1573 | 990.1 | 833.3 |

**Best size:** Avro(Generic) (0,09 KB/rec) — **Best enc:** Custom(TLV) (4761,9 Krec/s) — **Best dec:** Custom(TLV) (7142,9 Krec/s)

## G.Skewed-Ages | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0867 | 2057.6 | 1440.9 |
| Custom(TLV) | 0.0948 | 4950.5 | 4273.5 |
| Protobuf(Struct) | 0.1572 | 990.1 | 885.0 |

**Best size:** Avro(Generic) (0,09 KB/rec) — **Best enc:** Custom(TLV) (4950,5 Krec/s) — **Best dec:** Custom(TLV) (4273,5 Krec/s)

## H.Mixed-Realistic | N=10000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0992 | 2500.0 | 1250.0 |
| Custom(TLV) | 0.1070 | 5000.0 | 10000.0 |
| Protobuf(Struct) | 0.1768 | 1000.0 | 909.1 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (5000,0 Krec/s) — **Best dec:** Custom(TLV) (10000,0 Krec/s)

## H.Mixed-Realistic | N=100000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0993 | 1315.8 | 1030.9 |
| Custom(TLV) | 0.1071 | 1960.8 | 3846.2 |
| Protobuf(Struct) | 0.1769 | 757.6 | 877.2 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (1960,8 Krec/s) — **Best dec:** Custom(TLV) (3846,2 Krec/s)

## H.Mixed-Realistic | N=500000

| Format | KB/rec | Enc Krec/s | Dec Krec/s |
|---|---:|---:|---:|
| Avro(Generic) | 0.0992 | 1048.2 | 929.4 |
| Custom(TLV) | 0.1071 | 2066.1 | 3731.3 |
| Protobuf(Struct) | 0.1769 | 727.8 | 881.8 |

**Best size:** Avro(Generic) (0,10 KB/rec) — **Best enc:** Custom(TLV) (2066,1 Krec/s) — **Best dec:** Custom(TLV) (3731,3 Krec/s)


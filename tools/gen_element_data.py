#!/usr/bin/env python3
"""Generates the Java element and isotope tables for the Particle Accelerator mod.

The data below is the real periodic table: name, symbol, atomic number, standard atomic
weight, element category and stability class for all 118 known elements, plus the notable
isotopes of every element with their mass number, atomic mass, half life and decay mode.

Isotope half lives are stored in seconds; -1 marks a stable (or observationally stable)
nuclide. Only the physics-relevant nuclides are listed (natural abundances plus the
radioactive species that matter for decay chains, activation and element discovery).

Run with:  python3 tools/gen_element_data.py
"""

import os

# (Z, symbol, name, category, atomic weight u, stability)
# category: ALKALI, ALKALINE, TRANSITION, POST_TRANSITION, METALLOID, NONMETAL, HALOGEN,
#           NOBLE_GAS, LANTHANIDE, ACTINIDE, TRANSACTINIDE
# stability: STABLE, RADIOACTIVE (primordial / natural), SYNTHETIC
ELEMENTS = [
    (1, "H", "Hydrogen", "NONMETAL", 1.008, "STABLE"),
    (2, "He", "Helium", "NOBLE_GAS", 4.0026, "STABLE"),
    (3, "Li", "Lithium", "ALKALI", 6.94, "STABLE"),
    (4, "Be", "Beryllium", "ALKALINE", 9.0122, "STABLE"),
    (5, "B", "Boron", "METALLOID", 10.81, "STABLE"),
    (6, "C", "Carbon", "NONMETAL", 12.011, "STABLE"),
    (7, "N", "Nitrogen", "NONMETAL", 14.007, "STABLE"),
    (8, "O", "Oxygen", "NONMETAL", 15.999, "STABLE"),
    (9, "F", "Fluorine", "HALOGEN", 18.998, "STABLE"),
    (10, "Ne", "Neon", "NOBLE_GAS", 20.180, "STABLE"),
    (11, "Na", "Sodium", "ALKALI", 22.990, "STABLE"),
    (12, "Mg", "Magnesium", "ALKALINE", 24.305, "STABLE"),
    (13, "Al", "Aluminium", "POST_TRANSITION", 26.982, "STABLE"),
    (14, "Si", "Silicon", "METALLOID", 28.085, "STABLE"),
    (15, "P", "Phosphorus", "NONMETAL", 30.974, "STABLE"),
    (16, "S", "Sulfur", "NONMETAL", 32.06, "STABLE"),
    (17, "Cl", "Chlorine", "HALOGEN", 35.45, "STABLE"),
    (18, "Ar", "Argon", "NOBLE_GAS", 39.948, "STABLE"),
    (19, "K", "Potassium", "ALKALI", 39.098, "RADIOACTIVE"),
    (20, "Ca", "Calcium", "ALKALINE", 40.078, "STABLE"),
    (21, "Sc", "Scandium", "TRANSITION", 44.956, "STABLE"),
    (22, "Ti", "Titanium", "TRANSITION", 47.867, "STABLE"),
    (23, "V", "Vanadium", "TRANSITION", 50.942, "RADIOACTIVE"),
    (24, "Cr", "Chromium", "TRANSITION", 51.996, "STABLE"),
    (25, "Mn", "Manganese", "TRANSITION", 54.938, "STABLE"),
    (26, "Fe", "Iron", "TRANSITION", 55.845, "STABLE"),
    (27, "Co", "Cobalt", "TRANSITION", 58.933, "STABLE"),
    (28, "Ni", "Nickel", "TRANSITION", 58.693, "STABLE"),
    (29, "Cu", "Copper", "TRANSITION", 63.546, "STABLE"),
    (30, "Zn", "Zinc", "TRANSITION", 65.38, "STABLE"),
    (31, "Ga", "Gallium", "POST_TRANSITION", 69.723, "STABLE"),
    (32, "Ge", "Germanium", "METALLOID", 72.630, "STABLE"),
    (33, "As", "Arsenic", "METALLOID", 74.922, "STABLE"),
    (34, "Se", "Selenium", "NONMETAL", 78.971, "RADIOACTIVE"),
    (35, "Br", "Bromine", "HALOGEN", 79.904, "STABLE"),
    (36, "Kr", "Krypton", "NOBLE_GAS", 83.798, "STABLE"),
    (37, "Rb", "Rubidium", "ALKALI", 85.468, "RADIOACTIVE"),
    (38, "Sr", "Strontium", "ALKALINE", 87.62, "STABLE"),
    (39, "Y", "Yttrium", "TRANSITION", 88.906, "STABLE"),
    (40, "Zr", "Zirconium", "TRANSITION", 91.224, "STABLE"),
    (41, "Nb", "Niobium", "TRANSITION", 92.906, "STABLE"),
    (42, "Mo", "Molybdenum", "TRANSITION", 95.95, "STABLE"),
    (43, "Tc", "Technetium", "TRANSITION", 98.0, "SYNTHETIC"),
    (44, "Ru", "Ruthenium", "TRANSITION", 101.07, "STABLE"),
    (45, "Rh", "Rhodium", "TRANSITION", 102.91, "STABLE"),
    (46, "Pd", "Palladium", "TRANSITION", 106.42, "STABLE"),
    (47, "Ag", "Silver", "TRANSITION", 107.87, "STABLE"),
    (48, "Cd", "Cadmium", "TRANSITION", 112.41, "STABLE"),
    (49, "In", "Indium", "POST_TRANSITION", 114.82, "RADIOACTIVE"),
    (50, "Sn", "Tin", "POST_TRANSITION", 118.71, "STABLE"),
    (51, "Sb", "Antimony", "METALLOID", 121.76, "STABLE"),
    (52, "Te", "Tellurium", "METALLOID", 127.60, "RADIOACTIVE"),
    (53, "I", "Iodine", "HALOGEN", 126.90, "STABLE"),
    (54, "Xe", "Xenon", "NOBLE_GAS", 131.29, "STABLE"),
    (55, "Cs", "Caesium", "ALKALI", 132.91, "STABLE"),
    (56, "Ba", "Barium", "ALKALINE", 137.33, "STABLE"),
    (57, "La", "Lanthanum", "LANTHANIDE", 138.91, "RADIOACTIVE"),
    (58, "Ce", "Cerium", "LANTHANIDE", 140.12, "STABLE"),
    (59, "Pr", "Praseodymium", "LANTHANIDE", 140.91, "STABLE"),
    (60, "Nd", "Neodymium", "LANTHANIDE", 144.24, "RADIOACTIVE"),
    (61, "Pm", "Promethium", "LANTHANIDE", 145.0, "SYNTHETIC"),
    (62, "Sm", "Samarium", "LANTHANIDE", 150.36, "RADIOACTIVE"),
    (63, "Eu", "Europium", "LANTHANIDE", 151.96, "RADIOACTIVE"),
    (64, "Gd", "Gadolinium", "LANTHANIDE", 157.25, "STABLE"),
    (65, "Tb", "Terbium", "LANTHANIDE", 158.93, "STABLE"),
    (66, "Dy", "Dysprosium", "LANTHANIDE", 162.50, "STABLE"),
    (67, "Ho", "Holmium", "LANTHANIDE", 164.93, "STABLE"),
    (68, "Er", "Erbium", "LANTHANIDE", 167.26, "STABLE"),
    (69, "Tm", "Thulium", "LANTHANIDE", 168.93, "STABLE"),
    (70, "Yb", "Ytterbium", "LANTHANIDE", 173.05, "STABLE"),
    (71, "Lu", "Lutetium", "LANTHANIDE", 174.97, "RADIOACTIVE"),
    (72, "Hf", "Hafnium", "TRANSITION", 178.49, "RADIOACTIVE"),
    (73, "Ta", "Tantalum", "TRANSITION", 180.95, "STABLE"),
    (74, "W", "Tungsten", "TRANSITION", 183.84, "RADIOACTIVE"),
    (75, "Re", "Rhenium", "TRANSITION", 186.21, "RADIOACTIVE"),
    (76, "Os", "Osmium", "TRANSITION", 190.23, "RADIOACTIVE"),
    (77, "Ir", "Iridium", "TRANSITION", 192.22, "STABLE"),
    (78, "Pt", "Platinum", "TRANSITION", 195.08, "RADIOACTIVE"),
    (79, "Au", "Gold", "TRANSITION", 196.97, "STABLE"),
    (80, "Hg", "Mercury", "TRANSITION", 200.59, "STABLE"),
    (81, "Tl", "Thallium", "POST_TRANSITION", 204.38, "STABLE"),
    (82, "Pb", "Lead", "POST_TRANSITION", 207.2, "RADIOACTIVE"),
    (83, "Bi", "Bismuth", "POST_TRANSITION", 208.98, "RADIOACTIVE"),
    (84, "Po", "Polonium", "POST_TRANSITION", 209.0, "RADIOACTIVE"),
    (85, "At", "Astatine", "HALOGEN", 210.0, "SYNTHETIC"),
    (86, "Rn", "Radon", "NOBLE_GAS", 222.0, "RADIOACTIVE"),
    (87, "Fr", "Francium", "ALKALI", 223.0, "RADIOACTIVE"),
    (88, "Ra", "Radium", "ALKALINE", 226.0, "RADIOACTIVE"),
    (89, "Ac", "Actinium", "ACTINIDE", 227.0, "RADIOACTIVE"),
    (90, "Th", "Thorium", "ACTINIDE", 232.04, "RADIOACTIVE"),
    (91, "Pa", "Protactinium", "ACTINIDE", 231.04, "RADIOACTIVE"),
    (92, "U", "Uranium", "ACTINIDE", 238.03, "RADIOACTIVE"),
    (93, "Np", "Neptunium", "ACTINIDE", 237.0, "SYNTHETIC"),
    (94, "Pu", "Plutonium", "ACTINIDE", 244.0, "SYNTHETIC"),
    (95, "Am", "Americium", "ACTINIDE", 243.0, "SYNTHETIC"),
    (96, "Cm", "Curium", "ACTINIDE", 247.0, "SYNTHETIC"),
    (97, "Bk", "Berkelium", "ACTINIDE", 247.0, "SYNTHETIC"),
    (98, "Cf", "Californium", "ACTINIDE", 251.0, "SYNTHETIC"),
    (99, "Es", "Einsteinium", "ACTINIDE", 252.0, "SYNTHETIC"),
    (100, "Fm", "Fermium", "ACTINIDE", 257.0, "SYNTHETIC"),
    (101, "Md", "Mendelevium", "ACTINIDE", 258.0, "SYNTHETIC"),
    (102, "No", "Nobelium", "ACTINIDE", 259.0, "SYNTHETIC"),
    (103, "Lr", "Lawrencium", "ACTINIDE", 266.0, "SYNTHETIC"),
    (104, "Rf", "Rutherfordium", "TRANSACTINIDE", 267.0, "SYNTHETIC"),
    (105, "Db", "Dubnium", "TRANSACTINIDE", 268.0, "SYNTHETIC"),
    (106, "Sg", "Seaborgium", "TRANSACTINIDE", 269.0, "SYNTHETIC"),
    (107, "Bh", "Bohrium", "TRANSACTINIDE", 270.0, "SYNTHETIC"),
    (108, "Hs", "Hassium", "TRANSACTINIDE", 269.0, "SYNTHETIC"),
    (109, "Mt", "Meitnerium", "TRANSACTINIDE", 278.0, "SYNTHETIC"),
    (110, "Ds", "Darmstadtium", "TRANSACTINIDE", 281.0, "SYNTHETIC"),
    (111, "Rg", "Roentgenium", "TRANSACTINIDE", 282.0, "SYNTHETIC"),
    (112, "Cn", "Copernicium", "TRANSACTINIDE", 285.0, "SYNTHETIC"),
    (113, "Nh", "Nihonium", "TRANSACTINIDE", 286.0, "SYNTHETIC"),
    (114, "Fl", "Flerovium", "TRANSACTINIDE", 289.0, "SYNTHETIC"),
    (115, "Mc", "Moscovium", "TRANSACTINIDE", 290.0, "SYNTHETIC"),
    (116, "Lv", "Livermorium", "TRANSACTINIDE", 293.0, "SYNTHETIC"),
    (117, "Ts", "Tennessine", "TRANSACTINIDE", 294.0, "SYNTHETIC"),
    (118, "Og", "Oganesson", "TRANSACTINIDE", 294.0, "SYNTHETIC"),
]

YEAR = 3.15569e7
DAY = 86400.0
HOUR = 3600.0
MINUTE = 60.0
MS = 1.0e-3
US = 1.0e-6

# Z -> list of (A, mass u, half life s (-1 = stable), decay mode, natural abundance %)
# decay modes: STABLE, ALPHA, BETA_MINUS, BETA_PLUS, EC, IT, SF, DOUBLE_BETA, SPALLATION
ISOTOPES = {
    1: [(1, 1.007825, -1, "STABLE", 99.985), (2, 2.014102, -1, "STABLE", 0.015),
        (3, 3.016049, 12.32 * YEAR, "BETA_MINUS", 0.0)],
    2: [(3, 3.016029, -1, "STABLE", 0.0002), (4, 4.002603, -1, "STABLE", 99.9998),
        (6, 6.018889, 0.8067, "BETA_MINUS", 0.0), (8, 8.033922, 0.1191, "BETA_MINUS", 0.0)],
    3: [(6, 6.015123, -1, "STABLE", 7.59), (7, 7.016004, -1, "STABLE", 92.41),
        (8, 8.022487, 0.838, "BETA_MINUS", 0.0), (11, 11.043798, 0.00875, "BETA_MINUS", 0.0)],
    4: [(7, 7.016929, 53.22 * DAY, "EC", 0.0), (9, 9.012183, -1, "STABLE", 100.0),
        (10, 10.013535, 1.51e6 * YEAR, "BETA_MINUS", 0.0)],
    5: [(8, 8.024607, 0.77, "BETA_PLUS", 0.0), (10, 10.012937, -1, "STABLE", 19.9),
        (11, 11.009305, -1, "STABLE", 80.1)],
    6: [(11, 11.011433, 20.36 * MINUTE, "BETA_PLUS", 0.0), (12, 12.000000, -1, "STABLE", 98.93),
        (13, 13.003355, -1, "STABLE", 1.07), (14, 14.003242, 5730.0 * YEAR, "BETA_MINUS", 0.0)],
    7: [(13, 13.005739, 9.965 * MINUTE, "BETA_PLUS", 0.0), (14, 14.003074, -1, "STABLE", 99.636),
        (15, 15.000109, -1, "STABLE", 0.364), (16, 16.006101, 7.13, "BETA_MINUS", 0.0)],
    8: [(15, 15.003066, 122.24, "BETA_PLUS", 0.0), (16, 15.994915, -1, "STABLE", 99.757),
        (17, 16.999132, -1, "STABLE", 0.038), (18, 17.999160, -1, "STABLE", 0.205)],
    9: [(18, 18.000938, 109.77 * MINUTE, "BETA_PLUS", 0.0), (19, 18.998403, -1, "STABLE", 100.0)],
    10: [(20, 19.992440, -1, "STABLE", 90.48), (21, 20.993847, -1, "STABLE", 0.27),
         (22, 21.991385, -1, "STABLE", 9.25)],
    11: [(22, 21.994437, 2.6018 * YEAR, "BETA_PLUS", 0.0), (23, 22.989770, -1, "STABLE", 100.0),
         (24, 23.990963, 14.997 * HOUR, "BETA_MINUS", 0.0)],
    12: [(24, 23.985042, -1, "STABLE", 78.99), (25, 24.985837, -1, "STABLE", 10.00),
         (26, 25.982593, -1, "STABLE", 11.01), (28, 27.983877, 20.9 * HOUR, "BETA_MINUS", 0.0)],
    13: [(26, 25.986892, 7.17e5 * YEAR, "BETA_PLUS", 0.0), (27, 26.981538, -1, "STABLE", 100.0)],
    14: [(28, 27.976927, -1, "STABLE", 92.22), (29, 28.976495, -1, "STABLE", 4.69),
         (30, 29.973770, -1, "STABLE", 3.09), (32, 31.974148, 153.0 * YEAR, "BETA_MINUS", 0.0)],
    15: [(31, 30.973762, -1, "STABLE", 100.0), (32, 31.973907, 14.268 * DAY, "BETA_MINUS", 0.0),
         (33, 32.971725, 25.35 * DAY, "BETA_MINUS", 0.0)],
    16: [(32, 31.972071, -1, "STABLE", 94.99), (33, 32.971459, -1, "STABLE", 0.75),
         (34, 33.967867, -1, "STABLE", 4.25), (35, 34.969032, 87.51 * DAY, "BETA_MINUS", 0.0),
         (36, 35.967081, -1, "STABLE", 0.01)],
    17: [(35, 34.968853, -1, "STABLE", 75.76), (36, 35.968307, 3.01e5 * YEAR, "BETA_MINUS", 0.0),
         (37, 36.965903, -1, "STABLE", 24.24)],
    18: [(36, 35.967545, -1, "STABLE", 0.334), (38, 37.962732, -1, "STABLE", 0.063),
         (39, 38.964313, 269.0 * YEAR, "BETA_MINUS", 0.0), (40, 39.962383, -1, "STABLE", 99.60)],
    19: [(39, 38.963706, -1, "STABLE", 93.258), (40, 39.963998, 1.248e9 * YEAR, "BETA_MINUS", 0.0117),
         (41, 40.961825, -1, "STABLE", 6.730)],
    20: [(40, 39.962591, -1, "STABLE", 96.94), (41, 40.962278, 9.94e4 * YEAR, "EC", 0.0),
         (42, 41.958618, -1, "STABLE", 0.647), (43, 42.958767, -1, "STABLE", 0.135),
         (44, 43.955482, -1, "STABLE", 2.086), (45, 44.956186, 162.61 * DAY, "BETA_MINUS", 0.0),
         (46, 45.953689, -1, "STABLE", 0.004), (48, 47.952534, 6.4e19 * YEAR, "DOUBLE_BETA", 0.187)],
    21: [(45, 44.955907, -1, "STABLE", 100.0), (46, 45.955168, 83.79 * DAY, "BETA_MINUS", 0.0)],
    22: [(44, 43.959690, 60.0 * YEAR, "EC", 0.0), (46, 45.952628, -1, "STABLE", 8.25),
         (47, 46.951759, -1, "STABLE", 7.44), (48, 47.947942, -1, "STABLE", 73.72),
         (49, 48.947866, -1, "STABLE", 5.41), (50, 49.944787, -1, "STABLE", 5.18)],
    23: [(50, 49.947159, 2.7e17 * YEAR, "EC", 0.250), (51, 50.943959, -1, "STABLE", 99.750)],
    24: [(50, 49.946044, -1, "STABLE", 4.345), (51, 50.944765, 27.7025 * DAY, "EC", 0.0),
         (52, 51.940508, -1, "STABLE", 83.789), (53, 52.940649, -1, "STABLE", 9.501),
         (54, 53.938880, -1, "STABLE", 2.365)],
    25: [(53, 52.941291, 3.7e6 * YEAR, "EC", 0.0), (54, 53.940358, 312.2 * DAY, "EC", 0.0),
         (55, 54.938044, -1, "STABLE", 100.0)],
    26: [(54, 53.939609, -1, "STABLE", 5.845), (55, 54.938292, 2.744 * YEAR, "EC", 0.0),
         (56, 55.934936, -1, "STABLE", 91.754), (57, 56.935393, -1, "STABLE", 2.119),
         (58, 57.933274, -1, "STABLE", 0.282), (60, 59.934070, 2.62e6 * YEAR, "BETA_MINUS", 0.0)],
    27: [(56, 55.939839, 77.236 * DAY, "BETA_PLUS", 0.0), (57, 56.936291, 271.74 * DAY, "EC", 0.0),
         (58, 57.935752, 70.86 * DAY, "EC", 0.0), (59, 58.933194, -1, "STABLE", 100.0),
         (60, 59.933816, 5.2714 * YEAR, "BETA_MINUS", 0.0)],
    28: [(58, 57.935342, -1, "STABLE", 68.077), (59, 58.934346, 7.6e4 * YEAR, "EC", 0.0),
         (60, 59.930786, -1, "STABLE", 26.223), (61, 60.931056, -1, "STABLE", 1.139),
         (62, 61.928345, -1, "STABLE", 3.635), (63, 62.929669, 100.1 * YEAR, "BETA_MINUS", 0.0),
         (64, 63.927966, -1, "STABLE", 0.926)],
    29: [(63, 62.929598, -1, "STABLE", 69.15), (64, 63.929764, 12.7004 * HOUR, "EC", 0.0),
         (65, 64.927790, -1, "STABLE", 30.85), (67, 66.927730, 61.83 * HOUR, "BETA_MINUS", 0.0)],
    30: [(64, 63.929142, -1, "STABLE", 49.17), (65, 64.929241, 243.93 * DAY, "EC", 0.0),
         (66, 65.926034, -1, "STABLE", 27.73), (67, 66.927128, -1, "STABLE", 4.04),
         (68, 67.924845, -1, "STABLE", 18.45), (70, 69.925320, -1, "STABLE", 0.61)],
    31: [(67, 66.928202, 3.2617 * DAY, "EC", 0.0), (69, 68.925574, -1, "STABLE", 60.108),
         (71, 70.924703, -1, "STABLE", 39.892)],
    32: [(70, 69.924250, -1, "STABLE", 20.52), (72, 71.922076, -1, "STABLE", 27.45),
         (73, 72.923459, -1, "STABLE", 7.76), (74, 73.921178, -1, "STABLE", 36.52),
         (76, 75.921403, 1.78e21 * YEAR, "DOUBLE_BETA", 7.75)],
    33: [(73, 72.923825, 80.30 * DAY, "EC", 0.0), (75, 74.921595, -1, "STABLE", 100.0),
         (76, 75.922392, 26.26 * HOUR, "BETA_MINUS", 0.0)],
    34: [(74, 73.922476, -1, "STABLE", 0.89), (76, 75.919214, -1, "STABLE", 9.37),
         (77, 76.919915, -1, "STABLE", 7.63), (78, 77.917310, -1, "STABLE", 23.77),
         (79, 78.918500, 3.27e5 * YEAR, "BETA_MINUS", 0.0), (80, 79.916522, -1, "STABLE", 49.61),
         (82, 81.916700, 9.2e19 * YEAR, "DOUBLE_BETA", 8.73)],
    35: [(77, 76.921380, 57.04 * HOUR, "EC", 0.0), (79, 78.918338, -1, "STABLE", 50.69),
         (81, 80.916290, -1, "STABLE", 49.31)],
    36: [(78, 77.920365, 9.2e21 * YEAR, "DOUBLE_BETA", 0.355), (80, 79.916379, -1, "STABLE", 2.286),
         (82, 81.913483, -1, "STABLE", 11.593), (83, 82.914136, -1, "STABLE", 11.500),
         (84, 83.911507, -1, "STABLE", 56.987), (85, 84.912527, 10.756 * YEAR, "BETA_MINUS", 0.0),
         (86, 85.910610, -1, "STABLE", 17.279)],
    37: [(85, 84.911789, -1, "STABLE", 72.17), (86, 85.911167, 18.642 * DAY, "BETA_MINUS", 0.0),
         (87, 86.909180, 4.923e10 * YEAR, "BETA_MINUS", 27.83)],
    38: [(84, 83.913419, -1, "STABLE", 0.56), (86, 85.909261, -1, "STABLE", 9.86),
         (87, 86.908877, -1, "STABLE", 7.00), (88, 87.905613, -1, "STABLE", 82.58),
         (90, 89.907738, 28.79 * YEAR, "BETA_MINUS", 0.0)],
    39: [(89, 88.905840, -1, "STABLE", 100.0), (90, 89.907152, 64.05 * HOUR, "BETA_MINUS", 0.0),
         (91, 90.907305, 58.51 * DAY, "BETA_MINUS", 0.0)],
    40: [(90, 89.904698, -1, "STABLE", 51.45), (91, 90.905640, -1, "STABLE", 11.22),
         (92, 91.905035, -1, "STABLE", 17.15), (93, 92.906470, 1.53e6 * YEAR, "BETA_MINUS", 0.0),
         (94, 93.906311, -1, "STABLE", 17.38), (96, 95.908272, 2.0e19 * YEAR, "DOUBLE_BETA", 2.80)],
    41: [(92, 91.907193, 3.47e7 * YEAR, "EC", 0.0), (93, 92.906373, -1, "STABLE", 100.0),
         (94, 93.907279, 2.03e4 * YEAR, "BETA_MINUS", 0.0)],
    42: [(92, 91.906808, -1, "STABLE", 14.53), (94, 93.905085, -1, "STABLE", 9.15),
         (95, 94.905839, -1, "STABLE", 15.84), (96, 95.904676, -1, "STABLE", 16.67),
         (97, 96.906018, -1, "STABLE", 9.60), (98, 97.905405, -1, "STABLE", 24.39),
         (99, 98.907709, 65.976 * HOUR, "BETA_MINUS", 0.0),
         (100, 99.907472, 7.3e18 * YEAR, "DOUBLE_BETA", 9.82)],
    43: [(97, 96.906366, 4.21e6 * YEAR, "EC", 0.0), (98, 97.907213, 4.2e6 * YEAR, "BETA_MINUS", 0.0),
         (99, 98.906255, 2.111e5 * YEAR, "BETA_MINUS", 0.0)],
    44: [(96, 95.907590, -1, "STABLE", 5.54), (98, 97.905287, -1, "STABLE", 1.87),
         (99, 98.905939, -1, "STABLE", 12.76), (100, 99.904220, -1, "STABLE", 12.60),
         (101, 100.905582, -1, "STABLE", 17.06), (102, 101.904350, -1, "STABLE", 31.55),
         (104, 103.905430, -1, "STABLE", 18.62), (106, 105.907329, 371.8 * DAY, "BETA_MINUS", 0.0)],
    45: [(101, 100.906164, 3.3 * YEAR, "EC", 0.0), (103, 102.905504, -1, "STABLE", 100.0)],
    46: [(102, 101.905609, -1, "STABLE", 1.02), (104, 103.904036, -1, "STABLE", 11.14),
         (105, 104.905085, -1, "STABLE", 22.33), (106, 105.903486, -1, "STABLE", 27.33),
         (107, 106.905134, 6.5e6 * YEAR, "BETA_MINUS", 0.0), (108, 107.903892, -1, "STABLE", 26.46),
         (110, 109.905153, -1, "STABLE", 11.72)],
    47: [(105, 104.906529, 41.29 * DAY, "EC", 0.0), (107, 106.905097, -1, "STABLE", 51.839),
         (108, 107.905955, 2.37 * MINUTE, "BETA_MINUS", 0.0), (109, 108.904752, -1, "STABLE", 48.161),
         (110, 109.906108, 24.6, "BETA_MINUS", 0.0), (111, 110.905291, 7.45 * DAY, "BETA_MINUS", 0.0)],
    48: [(106, 105.906461, -1, "STABLE", 1.25), (108, 107.904176, -1, "STABLE", 0.89),
         (109, 108.904982, 461.4 * DAY, "EC", 0.0), (110, 109.903005, -1, "STABLE", 12.49),
         (111, 110.904182, -1, "STABLE", 12.80), (112, 111.902758, -1, "STABLE", 24.13),
         (113, 112.904401, -1, "STABLE", 12.22), (114, 113.903359, -1, "STABLE", 28.73),
         (116, 115.904756, 2.8e19 * YEAR, "DOUBLE_BETA", 7.49)],
    49: [(111, 110.905103, 2.8047 * DAY, "EC", 0.0), (113, 112.904058, -1, "STABLE", 4.29),
         (115, 114.903878, 4.41e14 * YEAR, "BETA_MINUS", 95.71)],
    50: [(112, 111.904818, -1, "STABLE", 0.97), (114, 113.902779, -1, "STABLE", 0.66),
         (115, 114.903342, -1, "STABLE", 0.34), (116, 115.901741, -1, "STABLE", 14.54),
         (117, 116.902952, -1, "STABLE", 7.68), (118, 117.901603, -1, "STABLE", 24.22),
         (119, 118.903308, -1, "STABLE", 8.59), (120, 119.902195, -1, "STABLE", 32.58),
         (122, 121.903440, -1, "STABLE", 4.63), (124, 123.905274, -1, "STABLE", 5.79),
         (126, 125.907659, 2.3e5 * YEAR, "BETA_MINUS", 0.0)],
    51: [(121, 120.903812, -1, "STABLE", 57.21), (123, 122.904214, -1, "STABLE", 42.79),
         (125, 124.905253, 2.7586 * YEAR, "BETA_MINUS", 0.0)],
    52: [(120, 119.904020, -1, "STABLE", 0.09), (122, 121.903044, -1, "STABLE", 2.55),
         (123, 122.904270, 9.2e16 * YEAR, "EC", 0.89), (124, 123.902818, -1, "STABLE", 4.74),
         (125, 124.904431, -1, "STABLE", 7.07), (126, 125.903312, -1, "STABLE", 18.84),
         (128, 127.904461, 2.2e24 * YEAR, "DOUBLE_BETA", 31.74),
         (130, 129.906223, 7.9e20 * YEAR, "DOUBLE_BETA", 34.08)],
    53: [(123, 122.905589, 13.2235 * HOUR, "EC", 0.0), (127, 126.904473, -1, "STABLE", 100.0),
         (129, 128.904988, 1.57e7 * YEAR, "BETA_MINUS", 0.0),
         (131, 130.906126, 8.0252 * DAY, "BETA_MINUS", 0.0)],
    54: [(124, 123.905893, 1.8e22 * YEAR, "DOUBLE_BETA", 0.095), (126, 125.904274, -1, "STABLE", 0.089),
         (128, 127.903531, -1, "STABLE", 1.910), (129, 128.904779, -1, "STABLE", 26.401),
         (130, 129.903508, -1, "STABLE", 4.071), (131, 130.905082, -1, "STABLE", 21.232),
         (132, 131.904154, -1, "STABLE", 26.909), (133, 132.905911, 5.247 * DAY, "BETA_MINUS", 0.0),
         (134, 133.905395, -1, "STABLE", 10.436), (136, 135.907219, 2.2e21 * YEAR, "DOUBLE_BETA", 8.857)],
    55: [(133, 132.905452, -1, "STABLE", 100.0), (134, 133.906718, 2.0642 * YEAR, "BETA_MINUS", 0.0),
         (135, 134.905977, 2.3e6 * YEAR, "BETA_MINUS", 0.0),
         (137, 136.907090, 30.08 * YEAR, "BETA_MINUS", 0.0)],
    56: [(130, 129.906321, 1.2e21 * YEAR, "DOUBLE_BETA", 0.106), (132, 131.905061, -1, "STABLE", 0.101),
         (133, 132.906008, 10.551 * YEAR, "EC", 0.0), (134, 133.904509, -1, "STABLE", 2.417),
         (135, 134.905978, -1, "STABLE", 6.592), (136, 135.904576, -1, "STABLE", 7.854),
         (137, 136.905827, -1, "STABLE", 11.232), (138, 137.905247, -1, "STABLE", 71.698),
         (140, 139.910605, 12.7527 * DAY, "BETA_MINUS", 0.0)],
    57: [(138, 137.907112, 1.02e11 * YEAR, "BETA_MINUS", 0.090), (139, 138.906353, -1, "STABLE", 99.910),
         (140, 139.909477, 1.6781 * DAY, "BETA_MINUS", 0.0)],
    58: [(136, 135.907172, -1, "STABLE", 0.185), (138, 137.905991, -1, "STABLE", 0.251),
         (140, 139.905439, -1, "STABLE", 88.450), (141, 140.908277, 32.508 * DAY, "BETA_MINUS", 0.0),
         (142, 141.909244, -1, "STABLE", 11.114), (144, 143.913647, 284.91 * DAY, "BETA_MINUS", 0.0)],
    59: [(141, 140.907653, -1, "STABLE", 100.0), (143, 142.910933, 13.57 * DAY, "BETA_MINUS", 0.0)],
    60: [(142, 141.907723, -1, "STABLE", 27.152), (143, 142.909814, -1, "STABLE", 12.174),
         (144, 143.910087, 2.29e15 * YEAR, "ALPHA", 23.798), (145, 144.912574, -1, "STABLE", 8.293),
         (146, 145.913117, -1, "STABLE", 17.189), (147, 146.916101, 10.98 * DAY, "BETA_MINUS", 0.0),
         (148, 147.916893, -1, "STABLE", 5.756), (150, 149.920891, 9.3e18 * YEAR, "DOUBLE_BETA", 5.638)],
    61: [(145, 144.912744, 17.7 * YEAR, "EC", 0.0), (147, 146.915139, 2.6234 * YEAR, "BETA_MINUS", 0.0),
         (149, 148.918334, 53.08 * HOUR, "BETA_MINUS", 0.0)],
    62: [(144, 143.912007, -1, "STABLE", 3.07), (146, 145.913041, 1.03e8 * YEAR, "ALPHA", 0.0),
         (147, 146.914898, 1.06e11 * YEAR, "ALPHA", 14.99), (148, 147.914823, 7e15 * YEAR, "ALPHA", 11.24),
         (149, 148.917192, -1, "STABLE", 13.82), (150, 149.917282, -1, "STABLE", 7.38),
         (151, 150.919939, 90.0 * YEAR, "BETA_MINUS", 0.0), (152, 151.919739, -1, "STABLE", 26.75),
         (153, 152.922104, 46.284 * HOUR, "BETA_MINUS", 0.0), (154, 153.922216, -1, "STABLE", 22.75)],
    63: [(151, 150.919850, 4.62e18 * YEAR, "ALPHA", 47.81), (152, 151.921744, 13.517 * YEAR, "EC", 0.0),
         (153, 152.921230, -1, "STABLE", 52.19), (154, 153.922979, 8.601 * YEAR, "BETA_MINUS", 0.0),
         (155, 154.922893, 4.753 * YEAR, "BETA_MINUS", 0.0)],
    64: [(152, 151.919792, 1.08e14 * YEAR, "ALPHA", 0.20), (153, 152.921750, 240.4 * DAY, "EC", 0.0),
         (154, 153.920874, -1, "STABLE", 2.18), (155, 154.922630, -1, "STABLE", 14.80),
         (156, 155.922131, -1, "STABLE", 20.47), (157, 156.923968, -1, "STABLE", 15.65),
         (158, 157.924112, -1, "STABLE", 24.84), (160, 159.927062, 2e19 * YEAR, "DOUBLE_BETA", 21.86)],
    65: [(157, 156.924033, 99.0 * YEAR, "EC", 0.0), (158, 157.925420, 180.0 * YEAR, "EC", 0.0),
         (159, 158.925354, -1, "STABLE", 100.0), (160, 159.927175, 72.3 * DAY, "BETA_MINUS", 0.0)],
    66: [(156, 155.924284, -1, "STABLE", 0.056), (158, 157.924415, -1, "STABLE", 0.095),
         (160, 159.925204, -1, "STABLE", 2.329), (161, 160.926940, -1, "STABLE", 18.889),
         (162, 161.926805, -1, "STABLE", 25.475), (163, 162.928738, -1, "STABLE", 24.896),
         (164, 163.929181, -1, "STABLE", 28.260)],
    67: [(163, 162.928734, 4570.0 * YEAR, "EC", 0.0), (165, 164.930329, -1, "STABLE", 100.0),
         (166, 165.932813, 1200.0 * YEAR, "BETA_MINUS", 0.0)],
    68: [(162, 161.928789, -1, "STABLE", 0.139), (164, 163.929209, -1, "STABLE", 1.601),
         (166, 165.930299, -1, "STABLE", 33.503), (167, 166.932055, -1, "STABLE", 22.869),
         (168, 167.932376, -1, "STABLE", 26.978), (169, 168.934596, 9.4 * DAY, "BETA_MINUS", 0.0),
         (170, 169.935471, -1, "STABLE", 14.910)],
    69: [(168, 167.934177, 93.1 * DAY, "EC", 0.0), (169, 168.934218, -1, "STABLE", 100.0),
         (170, 169.935807, 128.6 * DAY, "BETA_MINUS", 0.0), (171, 170.936435, 1.92 * YEAR, "BETA_MINUS", 0.0)],
    70: [(168, 167.933897, -1, "STABLE", 0.123), (169, 168.935190, 32.026 * DAY, "EC", 0.0),
         (170, 169.934762, -1, "STABLE", 2.982), (171, 170.936326, -1, "STABLE", 14.086),
         (172, 171.936382, -1, "STABLE", 21.686), (173, 172.938211, -1, "STABLE", 16.103),
         (174, 173.938862, -1, "STABLE", 31.896), (176, 175.942572, -1, "STABLE", 12.982)],
    71: [(173, 172.938216, 1.37 * YEAR, "EC", 0.0), (174, 173.940048, 3.31 * YEAR, "EC", 0.0),
         (175, 174.940772, -1, "STABLE", 97.401), (176, 175.942687, 3.76e10 * YEAR, "BETA_MINUS", 0.259),
         (177, 176.943760, 6.647 * DAY, "BETA_MINUS", 0.0)],
    72: [(174, 173.940046, 2e15 * YEAR, "ALPHA", 0.16), (176, 175.941409, -1, "STABLE", 5.26),
         (177, 176.943230, -1, "STABLE", 18.60), (178, 177.943708, -1, "STABLE", 27.28),
         (179, 178.945823, -1, "STABLE", 13.62), (180, 179.946557, -1, "STABLE", 35.08),
         (182, 181.950561, 8.9e6 * YEAR, "BETA_MINUS", 0.0)],
    73: [(180, 179.947471, 1.0e15 * YEAR, "IT", 0.012), (181, 180.947996, -1, "STABLE", 99.988)],
    74: [(180, 179.946711, 1.8e18 * YEAR, "ALPHA", 0.12), (182, 181.948205, -1, "STABLE", 26.50),
         (183, 182.950224, -1, "STABLE", 14.31), (184, 183.950933, -1, "STABLE", 30.64),
         (185, 184.953421, 75.1 * DAY, "BETA_MINUS", 0.0), (186, 185.954365, -1, "STABLE", 28.43)],
    75: [(185, 184.952956, -1, "STABLE", 37.40), (186, 185.954988, 3.7183 * DAY, "BETA_MINUS", 0.0),
         (187, 186.955751, 4.12e10 * YEAR, "BETA_MINUS", 62.60)],
    76: [(184, 183.952521, -1, "STABLE", 0.02), (186, 185.953840, 2e15 * YEAR, "ALPHA", 1.59),
         (187, 186.955753, -1, "STABLE", 1.96), (188, 187.955838, -1, "STABLE", 13.24),
         (189, 188.958147, -1, "STABLE", 16.15), (190, 189.958447, -1, "STABLE", 26.26),
         (191, 190.960931, 15.4 * DAY, "BETA_MINUS", 0.0), (192, 191.961481, -1, "STABLE", 40.78)],
    77: [(191, 190.960591, -1, "STABLE", 37.3), (192, 191.962605, 73.827 * DAY, "BETA_MINUS", 0.0),
         (193, 192.962924, -1, "STABLE", 62.7)],
    78: [(190, 189.959932, 6.5e11 * YEAR, "ALPHA", 0.012), (192, 191.961038, -1, "STABLE", 0.782),
         (193, 192.962987, 50.0 * YEAR, "EC", 0.0), (194, 193.962680, -1, "STABLE", 32.86),
         (195, 194.964791, -1, "STABLE", 33.78), (196, 195.964952, -1, "STABLE", 25.21),
         (198, 197.967893, -1, "STABLE", 7.356)],
    79: [(195, 194.965035, 186.01 * DAY, "EC", 0.0), (197, 196.966569, -1, "STABLE", 100.0),
         (198, 197.968243, 2.6941 * DAY, "BETA_MINUS", 0.0)],
    80: [(196, 195.965833, -1, "STABLE", 0.15), (198, 197.966769, -1, "STABLE", 9.97),
         (199, 198.968280, -1, "STABLE", 16.87), (200, 199.968326, -1, "STABLE", 23.10),
         (201, 200.970302, -1, "STABLE", 13.18), (202, 201.970643, -1, "STABLE", 29.86),
         (203, 202.972872, 46.594 * DAY, "BETA_MINUS", 0.0), (204, 203.973494, -1, "STABLE", 6.87)],
    81: [(201, 200.970820, 72.912 * HOUR, "EC", 0.0), (203, 202.972344, -1, "STABLE", 29.52),
         (204, 203.973864, 3.783 * YEAR, "BETA_MINUS", 0.0), (205, 204.974428, -1, "STABLE", 70.48)],
    82: [(204, 203.973044, -1, "STABLE", 1.4), (205, 204.974482, 1.73e7 * YEAR, "EC", 0.0),
         (206, 205.974465, -1, "STABLE", 24.1), (207, 206.975897, -1, "STABLE", 22.1),
         (208, 207.976652, -1, "STABLE", 52.4), (210, 209.984188, 22.2 * YEAR, "BETA_MINUS", 0.0),
         (211, 210.988737, 36.1 * MINUTE, "BETA_MINUS", 0.0), (212, 211.991898, 10.64 * HOUR, "BETA_MINUS", 0.0),
         (214, 213.999805, 26.8 * MINUTE, "BETA_MINUS", 0.0)],
    83: [(207, 206.978471, 31.55 * YEAR, "EC", 0.0), (208, 207.979742, 3.68e5 * YEAR, "EC", 0.0),
         (209, 208.980399, 2.01e19 * YEAR, "ALPHA", 100.0), (210, 209.984120, 5.012 * DAY, "BETA_MINUS", 0.0),
         (212, 211.991286, 60.55 * MINUTE, "BETA_MINUS", 0.0), (214, 213.998711, 19.9 * MINUTE, "BETA_MINUS", 0.0)],
    84: [(208, 207.981246, 2.898 * YEAR, "ALPHA", 0.0), (209, 208.982430, 124.0 * YEAR, "ALPHA", 0.0),
         (210, 209.982874, 138.376 * DAY, "ALPHA", 0.0), (214, 213.995201, 164.3 * US, "ALPHA", 0.0),
         (216, 216.001915, 0.145, "ALPHA", 0.0), (218, 218.008973, 3.10 * MINUTE, "ALPHA", 0.0)],
    85: [(210, 209.987148, 8.1 * HOUR, "EC", 0.0), (211, 210.987496, 7.214 * HOUR, "EC", 0.0),
         (219, 219.011162, 56.0, "ALPHA", 0.0)],
    86: [(211, 210.990601, 14.6 * HOUR, "EC", 0.0), (219, 219.009480, 3.96, "ALPHA", 0.0),
         (220, 220.011394, 55.6, "ALPHA", 0.0), (222, 222.017578, 3.8235 * DAY, "ALPHA", 0.0)],
    87: [(221, 221.014255, 4.8 * MINUTE, "ALPHA", 0.0), (223, 223.019736, 22.0 * MINUTE, "BETA_MINUS", 0.0)],
    88: [(223, 223.018502, 11.43 * DAY, "ALPHA", 0.0), (224, 224.020212, 3.6319 * DAY, "ALPHA", 0.0),
         (225, 225.023611, 14.9 * DAY, "BETA_MINUS", 0.0), (226, 226.025410, 1600.0 * YEAR, "ALPHA", 0.0),
         (228, 228.031070, 5.75 * YEAR, "BETA_MINUS", 0.0)],
    89: [(225, 225.023230, 10.0 * DAY, "ALPHA", 0.0), (227, 227.027752, 21.772 * YEAR, "BETA_MINUS", 0.0),
         (228, 228.031021, 6.15 * HOUR, "BETA_MINUS", 0.0)],
    90: [(227, 227.027704, 18.68 * DAY, "ALPHA", 0.0), (228, 228.028741, 1.9116 * YEAR, "ALPHA", 0.0),
         (229, 229.031762, 7917.0 * YEAR, "ALPHA", 0.0), (230, 230.033134, 75400.0 * YEAR, "ALPHA", 0.0),
         (231, 231.036304, 25.5 * HOUR, "BETA_MINUS", 0.0), (232, 232.038055, 1.405e10 * YEAR, "ALPHA", 100.0),
         (234, 234.043601, 24.10 * DAY, "BETA_MINUS", 0.0)],
    91: [(231, 231.035884, 32760.0 * YEAR, "ALPHA", 100.0), (233, 233.040247, 26.975 * DAY, "BETA_MINUS", 0.0),
         (234, 234.043308, 6.70 * HOUR, "BETA_MINUS", 0.0)],
    92: [(232, 232.037156, 68.9 * YEAR, "ALPHA", 0.0), (233, 233.039635, 1.592e5 * YEAR, "ALPHA", 0.0),
         (234, 234.040952, 2.455e5 * YEAR, "ALPHA", 0.0054),
         (235, 235.043930, 7.04e8 * YEAR, "ALPHA", 0.7204),
         (236, 236.045568, 2.342e7 * YEAR, "ALPHA", 0.0),
         (238, 238.050788, 4.468e9 * YEAR, "ALPHA", 99.2742)],
    93: [(235, 235.044063, 396.1 * DAY, "EC", 0.0), (236, 236.046570, 1.54e5 * YEAR, "EC", 0.0),
         (237, 237.048173, 2.144e6 * YEAR, "ALPHA", 0.0), (239, 239.052939, 2.356 * DAY, "BETA_MINUS", 0.0)],
    94: [(236, 236.046058, 2.858 * YEAR, "ALPHA", 0.0), (238, 238.049560, 87.7 * YEAR, "ALPHA", 0.0),
         (239, 239.052164, 24110.0 * YEAR, "ALPHA", 0.0), (240, 240.053814, 6561.0 * YEAR, "ALPHA", 0.0),
         (241, 241.056852, 14.329 * YEAR, "BETA_MINUS", 0.0), (242, 242.058743, 3.75e5 * YEAR, "ALPHA", 0.0),
         (244, 244.064205, 8.08e7 * YEAR, "ALPHA", 0.0)],
    95: [(241, 241.056829, 432.6 * YEAR, "ALPHA", 0.0), (242, 242.059549, 16.02 * HOUR, "BETA_MINUS", 0.0),
         (243, 243.061381, 7364.0 * YEAR, "ALPHA", 0.0)],
    96: [(242, 242.058835, 162.8 * DAY, "ALPHA", 0.0), (243, 243.061389, 29.1 * YEAR, "ALPHA", 0.0),
         (244, 244.062752, 18.1 * YEAR, "ALPHA", 0.0), (245, 245.065491, 8500.0 * YEAR, "ALPHA", 0.0),
         (246, 246.067223, 4760.0 * YEAR, "ALPHA", 0.0), (247, 247.070354, 1.56e7 * YEAR, "ALPHA", 0.0),
         (248, 248.072349, 3.48e5 * YEAR, "ALPHA", 0.0), (250, 250.078358, 8300.0 * YEAR, "SF", 0.0)],
    97: [(247, 247.070307, 1380.0 * YEAR, "ALPHA", 0.0), (249, 249.074986, 330.0 * DAY, "BETA_MINUS", 0.0)],
    98: [(248, 248.072185, 333.5 * DAY, "ALPHA", 0.0), (249, 249.074853, 351.0 * YEAR, "ALPHA", 0.0),
         (250, 250.076406, 13.08 * YEAR, "ALPHA", 0.0), (251, 251.079588, 898.0 * YEAR, "ALPHA", 0.0),
         (252, 252.081627, 2.645 * YEAR, "ALPHA", 0.0)],
    99: [(252, 252.082980, 471.7 * DAY, "ALPHA", 0.0), (253, 253.084825, 20.47 * DAY, "ALPHA", 0.0),
         (254, 254.088022, 275.7 * DAY, "BETA_MINUS", 0.0), (255, 255.090274, 39.8 * DAY, "BETA_MINUS", 0.0)],
    100: [(252, 252.082467, 25.39 * HOUR, "ALPHA", 0.0), (253, 253.085185, 3.0 * DAY, "EC", 0.0),
          (255, 255.089963, 20.07 * HOUR, "ALPHA", 0.0), (257, 257.095106, 100.5 * DAY, "ALPHA", 0.0)],
    101: [(256, 256.094060, 77.0 * MINUTE, "EC", 0.0), (258, 258.098431, 51.5 * DAY, "ALPHA", 0.0),
          (260, 260.103650, 31.8 * DAY, "SF", 0.0)],
    102: [(253, 253.090562, 1.62 * MINUTE, "ALPHA", 0.0), (255, 255.093241, 3.52 * MINUTE, "EC", 0.0),
          (259, 259.101030, 58.0 * MINUTE, "ALPHA", 0.0)],
    103: [(260, 260.105505, 2.7 * MINUTE, "ALPHA", 0.0), (262, 262.109610, 3.6 * HOUR, "EC", 0.0),
          (266, 266.119830, 11.0 * HOUR, "SF", 0.0)],
    104: [(263, 263.112547, 10.0 * MINUTE, "SF", 0.0), (265, 265.116700, 1.1 * MINUTE, "SF", 0.0),
          (267, 267.121790, 1.3 * HOUR, "SF", 0.0)],
    105: [(262, 262.114070, 34.0, "ALPHA", 0.0), (267, 267.122470, 1.2 * HOUR, "SF", 0.0),
          (268, 268.125670, 29.0 * HOUR, "SF", 0.0)],
    106: [(266, 266.120000, 21.0, "ALPHA", 0.0), (269, 269.128630, 14.0 * MINUTE, "ALPHA", 0.0),
          (271, 271.133470, 2.4 * MINUTE, "ALPHA", 0.0)],
    107: [(270, 270.133620, 61.0, "ALPHA", 0.0), (272, 272.138260, 9.8, "ALPHA", 0.0),
          (274, 274.143550, 40.0, "ALPHA", 0.0)],
    108: [(269, 269.133750, 9.7, "ALPHA", 0.0), (270, 270.134290, 7.6, "ALPHA", 0.0),
          (277, 277.151900, 11.0 * MINUTE, "SF", 0.0)],
    109: [(274, 274.147490, 0.44, "ALPHA", 0.0), (276, 276.151590, 0.62, "ALPHA", 0.0),
          (278, 278.156310, 4.5, "ALPHA", 0.0)],
    110: [(279, 279.158860, 0.20, "SF", 0.0), (281, 281.164510, 9.6, "SF", 0.0)],
    111: [(280, 280.164470, 4.6, "ALPHA", 0.0), (282, 282.169120, 100.0, "ALPHA", 0.0)],
    112: [(283, 283.173270, 4.2, "ALPHA", 0.0), (285, 285.177120, 29.0, "ALPHA", 0.0)],
    113: [(284, 284.178730, 0.48, "ALPHA", 0.0), (286, 286.182210, 9.5, "ALPHA", 0.0)],
    114: [(285, 285.183640, 0.10, "ALPHA", 0.0), (289, 289.190420, 1.9, "ALPHA", 0.0)],
    115: [(287, 287.190700, 0.037, "ALPHA", 0.0), (290, 290.196230, 0.016, "ALPHA", 0.0)],
    116: [(290, 290.198590, 0.0083, "ALPHA", 0.0), (293, 293.204490, 0.053, "ALPHA", 0.0)],
    117: [(293, 293.208240, 0.022, "ALPHA", 0.0), (294, 294.210460, 0.051, "ALPHA", 0.0)],
    118: [(294, 294.213920, 0.0007, "ALPHA", 0.0), (295, 295.216240, 0.00018, "ALPHA", 0.0)],
}

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "src", "main", "java", "com", "particlephysics", "elements")


def java_double(v):
    if v == int(v) and abs(v) < 1e15:
        return "%.1f" % v
    return repr(v)


def main():
    if not os.path.isdir(OUT_DIR):
        os.makedirs(OUT_DIR, exist_ok=True)

    with open(os.path.join(OUT_DIR, "ElementData.java"), "w") as f:
        f.write("""package com.particlephysics.elements;

/** Generated by tools/gen_element_data.py - do not edit by hand. */
final class ElementData {
    private ElementData() {
    }

    static final int COUNT = %d;

    /** Z for every element, 1..118. */
    static final int[] Z = {
""" % len(ELEMENTS))
        f.write("            " + ", ".join(str(e[0]) for e in ELEMENTS) + "\n    };\n\n")
        f.write("    /** Chemical symbols. */\n    static final String[] SYMBOL = {\n")
        f.write("\n".join('            ' + ", ".join('"%s"' % ELEMENTS[i][1] for i in range(a, min(a + 10, len(ELEMENTS)))) + ("," if a + 10 < len(ELEMENTS) else "") for a in range(0, len(ELEMENTS), 10)))
        f.write("\n    };\n\n")
        f.write("    /** Element names. */\n    static final String[] NAME = {\n")
        f.write("\n".join('            ' + ", ".join('"%s"' % ELEMENTS[i][2] for i in range(a, min(a + 6, len(ELEMENTS)))) + ("," if a + 6 < len(ELEMENTS) else "") for a in range(0, len(ELEMENTS), 6)))
        f.write("\n    };\n\n")
        f.write("    /** Element categories, see ElementData.CATEGORY_*. */\n    static final String[] CATEGORY = {\n")
        f.write("\n".join('            ' + ", ".join('"%s"' % ELEMENTS[i][3] for i in range(a, min(a + 8, len(ELEMENTS)))) + ("," if a + 8 < len(ELEMENTS) else "") for a in range(0, len(ELEMENTS), 8)))
        f.write("\n    };\n\n")
        f.write("    /** Standard atomic weights in u. */\n    static final double[] ATOMIC_MASS = {\n")
        f.write("\n".join('            ' + ", ".join(java_double(ELEMENTS[i][4]) for i in range(a, min(a + 6, len(ELEMENTS)))) + ("," if a + 6 < len(ELEMENTS) else "") for a in range(0, len(ELEMENTS), 6)))
        f.write("\n    };\n\n")
        f.write("    /** Stability classes, see ElementData.STABILITY_*. */\n    static final String[] STABILITY = {\n")
        f.write("\n".join('            ' + ", ".join('"%s"' % ELEMENTS[i][5] for i in range(a, min(a + 8, len(ELEMENTS)))) + ("," if a + 8 < len(ELEMENTS) else "") for a in range(0, len(ELEMENTS), 8)))
        f.write("\n    };\n}\n")

    rows = []
    for z in sorted(ISOTOPES):
        for (a, mass, half_life, mode, abundance) in ISOTOPES[z]:
            rows.append((z, a, mass, half_life, mode, abundance))

    with open(os.path.join(OUT_DIR, "IsotopeData.java"), "w") as f:
        f.write("""package com.particlephysics.elements;

/** Generated by tools/gen_element_data.py - do not edit by hand. */
final class IsotopeData {
    private IsotopeData() {
    }

    static final int COUNT = %d;
""" % len(rows))
        for name, fmt, idx in (
                ("PARENT_Z", "%d", 0), ("MASS_NUMBER", "%d", 1), ("HALF_LIFE_SECONDS", "%s", 3),
                ("ABUNDANCE", "%s", 5)):
            f.write("\n    static final %s[] %s = {\n" % ("int" if idx in (0, 1) else "double", name))
            values = []
            for r in rows:
                v = r[idx]
                if idx == 3:
                    values.append("-1.0" if v == -1 else java_double(float(v)))
                elif idx == 5:
                    values.append(java_double(float(v)))
                else:
                    values.append(str(v))
            f.write("\n".join('            ' + ", ".join(values[i:i + 10]) + ("," if i + 10 < len(values) else "") for i in range(0, len(values), 10)))
            f.write("\n    };\n")
        f.write("\n    /** Atomic masses in u. */\n    static final double[] ATOMIC_MASS = {\n")
        values = ["%s" % java_double(float(r[2])) for r in rows]
        f.write("\n".join('            ' + ", ".join(values[i:i + 8]) + ("," if i + 8 < len(values) else "") for i in range(0, len(values), 8)))
        f.write("\n    };\n\n    /** Decay modes. */\n    static final String[] DECAY_MODE = {\n")
        values = ['"%s"' % r[4] for r in rows]
        f.write("\n".join('            ' + ", ".join(values[i:i + 8]) + ("," if i + 8 < len(values) else "") for i in range(0, len(values), 8)))
        f.write("\n    };\n}\n")

    print("wrote %d elements and %d isotopes" % (len(ELEMENTS), len(rows)))


if __name__ == "__main__":
    main()

"""Conway's Game of Life in the terminal.

Usage:
    python game_of_life.py [--width W] [--height H] [--generations N]
                           [--density D] [--delay S] [--seed SEED]
"""

import argparse
import os
import random
import time

ALIVE = "█"
DEAD = " "


def random_grid(width, height, density, rng):
    return [[rng.random() < density for _ in range(width)] for _ in range(height)]


def count_neighbors(grid, row, col):
    height, width = len(grid), len(grid[0])
    total = 0
    for dr in (-1, 0, 1):
        for dc in (-1, 0, 1):
            if dr == 0 and dc == 0:
                continue
            # Wrap around the edges so the board behaves like a torus.
            if grid[(row + dr) % height][(col + dc) % width]:
                total += 1
    return total


def step(grid):
    new_grid = []
    for r, line in enumerate(grid):
        new_line = []
        for c, alive in enumerate(line):
            n = count_neighbors(grid, r, c)
            new_line.append(n == 3 or (alive and n == 2))
        new_grid.append(new_line)
    return new_grid


def render(grid, generation):
    population = sum(sum(line) for line in grid)
    rows = ["".join(ALIVE if cell else DEAD for cell in line) for line in grid]
    border = "+" + "-" * len(grid[0]) + "+"
    body = "\n".join(f"|{row}|" for row in rows)
    return f"{border}\n{body}\n{border}\nGeneration {generation}  Population {population}"


def clear_screen():
    print("\033[H\033[J", end="")


def main():
    parser = argparse.ArgumentParser(description="Conway's Game of Life")
    parser.add_argument("--width", type=int, default=60)
    parser.add_argument("--height", type=int, default=20)
    parser.add_argument("--generations", type=int, default=200)
    parser.add_argument("--density", type=float, default=0.3)
    parser.add_argument("--delay", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=None)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    grid = random_grid(args.width, args.height, args.density, rng)
    seen = set()

    try:
        for generation in range(args.generations):
            if os.isatty(1):
                clear_screen()
            print(render(grid, generation))

            state = tuple(tuple(line) for line in grid)
            if state in seen:
                print("Reached a repeating pattern. Stopping.")
                break
            seen.add(state)

            grid = step(grid)
            time.sleep(args.delay)
    except KeyboardInterrupt:
        print("\nInterrupted.")


if __name__ == "__main__":
    main()

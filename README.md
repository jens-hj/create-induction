# Create: Induction

A Create addon (NeoForge 1.21.1) for generators and motors that run on real Sable physics: a Dynamo Bearing spins a
magnet rotor as a physics sub-level, and Induction Coils turn the changing magnetic flux into Forge Energy or
Power Grid current, braking the rotor by exactly the power they take.

Player-facing description: [docs/MODRINTH.md](docs/MODRINTH.md).

## How it works

- `physics/Induction.java` – softened dipole flux through a coil, its gradient, and the forces and torques for a
  coil current. One coupling gives both EMF and force, so mechanical power equals electrical power.
- `content/coil` – coils are stepped once per Sable physics substep (`CoilPhysics`), read nearby magnets from
  Simulated's magnet map and queue forces on the bodies involved.
- `content/bearing` – assembles the structure on its face into a sub-level on a free hinge and applies drive torque
  directly; the stress impact follows the mechanical power delivered.
- `content/magnet` – a permanent magnet registered in Simulated's magnet map.
- `content/slipring` – two rings face to face on different bodies pair up and pass FE straight through; with Power
  Grid their terminals are joined by a small brush resistance.
- `compat/powergrid` – only loaded with Power Grid: the coil as a two-terminal voltage source.

## Requirements

Create 6.0.10, Sable 2.0.6, Create Aeronautics (Simulated 1.3.x). Power Grid 0.6.x is optional.

## Development

Tools come from the nix flake; run everything through `nix develop -c`.

```sh
nix develop -c ./gradlew build                      # compile + unit tests of the maths
# The dependencies are not redistributable through maven as runtime mods; take the jars from a modpack instance.
nix develop -c ./gradlew runGameTestServer -PpackMods=<instance>/mods
nix develop -c python3 scripts/headless.py ./gradlew runShowcase -PpackMods=<instance>/mods   # gallery screenshots, on Xvfb
nix develop -c python3 scripts/gen_textures.py      # regenerate textures and icon
nix develop -c ./gradlew modrinth -PmodrinthDryRun  # preview a release
```

`-PpackModNames=a,b,c` chooses which jars (by file name prefix) are linked into the run.

## License

MIT

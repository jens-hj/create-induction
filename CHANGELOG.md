# Changelog

## 0.2.1
- Fixed faces of neighbouring blocks disappearing next to coils, magnets and slip rings.

## 0.2.0
- New block: Slip Ring. Two of them face to face carry Forge Energy or Power Grid current across a turning joint, so coils can ride on the rotor with the magnets standing still around it.
- Coils and magnets on jointed physics structures (for example riveted on at an angle with Simulated Coasters) now work at full strength.
- The Dynamo Bearing only takes the power its network can spare: weak sources such as water wheels turn a loaded rotor slowly instead of overstressing.
- Fixed rotors jamming when the bearing was mounted on or next to other blocks.
- With Power Grid, a generating coil no longer lets a battery drain back through it.
- Coils no longer pass on energy they were given, only what they generate.
- New layered block models and textures, and a new icon.

## 0.1.0
- Initial release.
- Dynamo Bearing: spins a glued structure as a physics rotor from rotational force, drawing stress for the work it actually does.
- Permanent Magnet: an always-on magnet that also interacts with Redstone Magnets.
- Induction Coil: generates Forge Energy from magnets moving past it and brakes them in return; with a redstone signal it becomes an electromagnet.
- Optional Power Grid support: coils get wire terminals and act as a voltage source in a circuit.

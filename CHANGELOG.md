# Changelog

## 0.3.1
- Fixed the goggles showing only a Dynamo Bearing's idle stress impact. They now show what it really draws, which rises with the electrical load.
- A Dynamo Bearing no longer lists an empty "Generator Stats" entry while it is being driven.

## 0.3.0
- Electric motors. A Dynamo Bearing with nothing else turning its shaft now drives it from the rotor, so a rotor spun by coils powers Create machines.
- New block: Commutator. Paired with a Slip Ring it feeds the coils attached to it and reverses each one as it turns, making a self-starting brushed motor. Scroll to set the magnet pole count; negative reverses it.
- With Power Grid, wire a supply to the Slip Ring on a Commutator to run the motor from a circuit.

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

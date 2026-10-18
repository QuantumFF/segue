# ReplayGain is player volume unless the user turns on boost

By default ReplayGain is applied as player volume, because audio offload (used while the screen is off to let the CPU sleep) honors volume but skips audio processors. Volume can only attenuate, so positive gains stop at unity. Users who want quiet tracks raised opt into "Boost quiet tracks": an `AudioProcessor` in the audio sink applies the gain, up to +15 dB, behind a peak cap or limiter. Offload stays off for as long as boost is on. It flips once per setting change, never per track, so gapless transitions never cross between offload and PCM.

The processor stays in the PCM chain with boost off, copying buffers through at unity. The sink only re-reads which processors are active when it flushes (a song change or a real seek), so a processor that left the chain couldn't pick up boost mid-track. A seek to the current position is a no-op in ExoPlayer, so it can't force that flush. Don't take the processor out of the chain to save the copy.

## Considered options

- **Always apply ReplayGain in a processor.** That gives positive gains to everyone but turns off offload, and so battery savings, for every user.
- **Processor only while boost is on, seeking in place to switch.** Tried in 95ddd1a and reverted in fa07b31, because the in-place seek doesn't flush the sink.
- **Symfonium's "Disabled" normalisation**, which skips the conversion between Opus R128 (−23 LUFS) and ReplayGain (−18 LUFS) tags. Navidrome already converts R128 gains to the −18 LUFS reference when it scans, so here "Disabled" would equal −18 LUFS, and it's left out.

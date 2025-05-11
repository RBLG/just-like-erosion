
 - required step is started vanilla side
 - step is queued jle side and the thread is paused
 - chunk data is checked for requested step, if it isnt ready, queue required previous steps and pause thread, and so on

 ### sea/ground step
 step 1:
 - generate shore data through *something* noise

 ### elevation/river bed steps
 step 2:
 - requirement: sea data (step 1 data) until it find water + the safety ring
 - find all water sources in range and start step 2.5 in the range
 step 2.5:
 - requirement: terrain resistance data (step ?, -> data aware value + random value, in a range a..b so safety ring can be deduced)
 - flood fill random step the area
 - separated in regions to be flooded separately
 - store height data
 - store position of the source of the fill (or position where two branches are equal)

 ### river flow/width steps
 pre step 3: (as a flag in the flood fill propag?)
 - requirement: step 2.5 for the chunk
 - for every chunk traversed, require step 2 (same ring as step 2.5)
 step 3:
 - requirement: pre step 3
 - for every ridges, start downstream flood fill (can continue from upstream chunks that finished step 3)
 - store water flow (quantity?) data
 - store water flow fixed height data (height lower in river beds)

 ### surface steps
 go back to vanilla gen logic
 step 4.biome:
 - fill biome based on sea data, steepness, height

 step 4.surface:
 - fill world from river-flow-corrected height data
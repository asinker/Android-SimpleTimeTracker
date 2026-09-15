# Modifications in this fork

This repository is an unofficial, independently maintained fork of
[Razeeman/Android-SimpleTimeTracker](https://github.com/Razeeman/Android-SimpleTimeTracker).
It is not affiliated with or endorsed by the upstream maintainers.

Original project copyright remains with Anton Razinkov and the other upstream
contributors. Copyright in new contributions remains with their respective
contributors. The original Git history and authorship information are retained.

## Calendar snap release 1.59.2

Maintained by Asinker. First released on September 16, 2026.

This release contains all calendar editing improvements from 1.59.1 and adds:

- magnetic snapping of a moved block's start to a nearby previous block end;
- the same magnetic snapping when adjusting the block's start handle;
- target-day-aware snapping after moving a block to another visible date;
- saving and leaving calendar edit mode by tapping the block, either handle,
  or any other place in the calendar;
- automated tests for snap thresholds and day-boundary constraints.

The corresponding source snapshot is tagged `v1.59.2`.

## Calendar drag release 1.59.1

Maintained by Asinker. First released on September 15, 2026.

Compared with the upstream version used as its base, this release adds or changes:

- long-press direct dragging of calendar time blocks;
- top and bottom resize handles with theme-aware contrast;
- drag-preview text contrast improvements;
- moving a time block between visible dates in multi-day view;
- automatic timeline scrolling near the top and bottom edges;
- selection scale and highlight feedback while a block is held;
- an undo action after moving or resizing a time block;
- drag calculations that respect reversed layouts, zoom, shifted day boundaries,
  adjacent date columns, and daylight-saving transitions;
- automated tests for the calendar drag calculations.

The corresponding source snapshot is tagged `calendar-drag-v1.59.1`.

## Licensing

The Android application and modifications are distributed under GNU GPLv3 or
later, consistently with the upstream project. The Wear OS portion retains the
upstream MPL 2.0 notice. See [LICENSE.md](LICENSE.md) and the license section in
[README.md](README.md).

If a binary is distributed, recipients must also be given access to the complete
corresponding source for that binary under the applicable licenses. This fork
must not be presented as an official upstream release.

# Improve HhmmPickerDialog UI

The goal is to enhance the visual design of the custom time picker (`HhmmPickerDialog`) to make it look more modern and less "basic". This will be achieved by adding clearer selection indicators, improving typography contrast, and refining the layout.

## User Review Required

> [!IMPORTANT]
> The plan involves changing the font sizes and weights for the selected time. I'm choosing `headlineMedium` for the selected item and `titleMedium` for unselected items. If this feels too large, we can scale it back to `titleLarge` for the selected item.

## Proposed Changes

### UI Components

#### [MODIFY] [HhmmPickerDialog.kt](file:///Users/sura/Desktop/Roster/Roster Android Native App/app/src/main/java/com/surainvestments/roster/ui/components/HhmmPickerDialog.kt)

- **Selection Indicator**:
    - Update the selection band background to use `primaryContainer` with a slightly higher alpha (0.12f).
    - Add horizontal dividers (0.5dp height) at the top and bottom of the selection band using `outlineVariant`.
- **Layout Improvements**:
    - Add a colon ":" separator between the Hour and Minute wheels.
    - Increase horizontal spacing between wheels for better legibility.
- **Wheel Item Styling**:
    - Modify the `Wheel` composable to emphasize the selected item.
    - Selected item: Use `headlineMedium` typography, `FontWeight.Bold`, and `MaterialTheme.colorScheme.primary` color.
    - Unselected items: Use `titleMedium` typography and `MaterialTheme.colorScheme.onSurfaceVariant` (faded).
- **Smooth Transitions**:
    - Refine the `graphicsLayer` logic to provide a smoother scaling and alpha transition as items move into and out of the center.

## Verification Plan

### Manual Verification
- Deploy the app to a device/emulator.
- Open the Availability screen and edit a day's hours to trigger the `HhmmPickerDialog`.
- Verify the new styling:
    - Clearer selection band with dividers.
    - Pronounced selected item (bold, colored, larger).
    - Smooth scrolling and snapping behavior.
    - Proper spacing between hour, minute, and period wheels.
- Check both Light and Dark modes for color consistency.

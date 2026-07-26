# Migrate HhmmPickerDialog to Material 3 TimePicker (Dial)

The goal is to replace the custom wheel-based time picker with the standard Material 3 `TimePicker` (dial-style) as requested.

## User Review Required

> [!IMPORTANT]
> The Material 3 `TimePicker` takes up more space than the compact wheel picker. I will wrap it in an `AlertDialog` to maintain consistency with the current usage.
> I will use `is24Hour = false` by default (AM/PM) as the previous picker was AM/PM based, but the internal storage will remain "HH:mm" (24h).

## Proposed Changes

### UI Components

#### [MODIFY] [HhmmPickerDialog.kt](file:///Users/sura/Desktop/Roster/Roster Android Native App/app/src/main/java/com/surainvestments/roster/ui/components/HhmmPickerDialog.kt)

- **State Management**:
    - Use `rememberTimePickerState` instead of individual index states.
    - Parse the `initial` string ("HH:mm") to seed the state.
- **UI Replacement**:
    - Replace the `Box` containing `Wheel` components with the `TimePicker` composable.
    - Remove the custom `Wheel`, `centeredItemIndex`, and `WheelEdgeFade` composables as they are no longer needed.
- **Experimental Annotations**:
    - Add `@OptIn(ExperimentalMaterial3Api::class)` as `TimePicker` and `rememberTimePickerState` are experimental.
- **Confirmation Logic**:
    - Extract `hour` and `minute` from `TimePickerState` and format them back to "HH:mm" using the existing `hhmmFormatter`.

## Verification Plan

### Manual Verification
- Deploy the app and trigger the time picker from the Availability or Submit Hours screens.
- Verify the Dial layout appears correctly.
- Confirm that selecting a time and clicking "OK" correctly updates the parent screen with the formatted "HH:mm" string.
- Test both AM and PM transitions.

# SISL-CLJS

A ClojureScript implementation of the SISL task for the web/MTurk.

## Development Dependencies

- Java 1.8
- [Leiningen 2.0](https://github.com/technomancy/leiningen)

## Setup

Install Java and Leiningen. Then navigate to this directory on the command line and run:

    lein clean
    lein cljsbuild once min
    lein ring server-headless

Then you can open a browser and connect to http://localhost:3700/.

Trials and scenarios are stored under `resources/public/trial` and `resources/public/scenario` respectively. When the user enters their trial ID in the app, it will request `resources/public/trial/{trial-id}.csv` and `resources/public/scenario/{trial-id}.json`.

On the server side, it will generate a unique ID for the session, and logs are written to `logs/{participant}/{session-id}/`. At the end of the trial, the session-id will be displayed. It's a UUID, but writing down the first 5 or so digits should be sufficient to differentiate it from other files in the same directory.

## Scenario Files

Scenario files are JSON. What each field does is described in `src/sisl_cljs/settings.cljs` in the comments on the `defaults` variable (this is also where the default values are stored). The notation there isn't exactly JSON (it's EDN, Clojure's native format), but there is a 1:1 correspondence. An example JSON version is available in `resources/public/scenario.json`.

## Trial Files

Trial files are CSV with the following columns:

- `cue_row_id` gets passed through to the log files, and otherwise has no effect.
- `type` contains the event type, and `value` contains its arguments. there are currently:
  - `cue` - spawns a new cue on the field, `value` indicates which lane
  - `dialog` - pauses the game and displays a dialog box, `value` indicates which dialog (pulled from a list in the scenario file)
  - `speed` - changes how speed adjustment is handled
	- -1 resets the speed to default (as defined in scenario)
	- 0 disables automatic speed adjustment
	- 1 enables automatic speed adjustment
	- 2 manually sets the speed multiplier based on the `time_to_targ_ms` field divided by 1000

  - `score` - adjusts scoring
	- -1 resets the score tracker
	- 0 disables scoring
	- 1 enables scoring

  - `scenario` - sets a scenario parameter. `value` is the new value to use, `category` column contains the key-path in the JSON file, with path components separated by dots. For example, if you wanted to change the default speed to 2x, you would add a row with `type` as "scenario", `value` as 2.0, and `category` as "speed.default".

- `appear_time_ms` - the time (in ms) between the previous event firing and this one firing
- `time_to_targ_ms` - the time (in ms) between a cue appearing and reaching the target zone, at the default speed setting
- `category` - passed through to the log file

- `audio_offset` - the offset between an event happening and the related audio cue. If audio is in `time` mode, this offset is how many milliseconds *before* the cue crosses the center of the target zone (a negative value would play the sound after the cue has already crossed the target). If audio is in `space` mode, it's measured in pixels instead. In `key` and `cue` modes, it does nothing.

## Log Files

Logs consist of 5 files:

- event.csv contains a log of the trial events.
- input.csv contains a log of the user's keyboard input during the trial.
- finish.json contains a timestamp for the final completion of the trial.
- scenario.json contains the scenario parameters as interpreted by the app
  (so there will be a few additional derived values on top of the ones present
  directly in the scenario file it was reading from)
- system.json contains information about the browser used

Both csv log files share the following columns:

- `date_time`: The time which the event happened.
- `time_stamp_ms`: A millisecond-resolution time stamp, relative to when the browser loaded the experiment page.
- `event_type`: What kind of event happened.

### Event Log

The event log has the following additional columns:

- `trial_row_id`: The row in the trial file that the event relates to.
- `event_value`: Depends on the event type.
  - If the event is cue-related, then this is 1 if the cue was hit correctly, and 0 if it was not, as of the time of the event.
  - If the event type is dialog_response, it is the response chosen for the dialog.
  - If the event type is speed_change, it is the new speed
  - If the event type is pause, it is 0 if the game was just paused, and 1 if the game was just resumed.
- `cue_pos_x` is the lane in which the relevant cue was.
- `cue_pos_y` is the y-value position of the top of the cue, relative to the top of the screen.
- `cue_vy` is the current velocity of the cue in pixels per millisecond.
- `cue_target_offset` is how far the cue would have to travel, in pixels, to be dead center in the target zone.
- `cue_category` comes from the trial file.

## License

Copyright © 2015-2019 SRI International

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

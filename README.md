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

Logs consist of 5 files:

- event.csv contains a log of the trial events.
- input.csv contains a log of the user's keyboard input during the trial.
- finish.json contains a timestamp for the final completion of the trial.
- scenario.json contains the scenario parameters as interpreted by the app
  (so there will be a few additional derived values on top of the ones present
  directly in the scenario file it was reading from)
- system.json contains information about the browser used

## Log Files

Both log files share the following columns:

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
- `cue\_pos\_x` is the lane in which the relevant cue was.
- `cue\_pos\_y` is the y-value position of the top of the cue, relative to the top of the screen.
- `cue_vy` is the current velocity of the cue in pixels per millisecond.
- `cue_target_offset` is how far the cue would have to travel, in pixels, to be dead center in the target zone.
- `cue_category` comes from the trial file.

## License

Copyright © 2015-2019 SRI International

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

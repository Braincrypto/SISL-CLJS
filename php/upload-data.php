<?php

include '/PATH/TO/SECRETS/FILE.php';

$error_log_file = "/var/tmp/webdasher_errors.log";
$current_row = null;
$method = $_SERVER['REQUEST_METHOD'];

if ($method != 'POST') {
    echo 'Access denied.';
    header('X-PHP-Response-Code: 403', true, 403);
    exit(0);
}

try {
    $result = array("success" => false);

    $inputJSON = file_get_contents('php://input');
    $data = json_decode($inputJSON, true);

    if(empty($data)) {
        error_log("Could not parse JSON\n" . $_POST['json'], 3, $error_log_file);
        throw new Exception('Could not parse JSON. Check it with jsonlint.com');
    }

    $link = new PDO( "mysql:host=localhost;dbname=$DB_NAME;charset=utf8mb4",
                     $DB_USER,
                     $DB_PASS,
                     array(
                         PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                         PDO::ATTR_PERSISTENT => false
                     )
    );

    $link->beginTransaction();


    $sessionID = $data['session'];
    $participantID = $data['participant'];

    $handle = $link->prepare('SELECT id, finish_tag, TIMESTAMPDIFF(SECOND, finish_time, NOW()) as dt FROM sessions WHERE id = ?;');
    $handle->bindValue(1, $sessionID);
    $handle->execute();
    $session_exists = ($handle->rowCount() == 1);

    if ($session_exists) {
        $row = $handle->fetch();
        if(isset($row['finish_tag']) && $row['dt'] > 60) {
            throw new Exception('Session finished more than 60s ago.');
        }
    } else {
        throw new Exception('Session '.$sessionID.' not found.');
    }

    if(is_array($data['data'])) {
        foreach($data['data'] as $row) {
            $current_row = $row;
            $handle = null;
            $fields = null;
            $values = array();

            $values['date_time'] = $row['date_time'];
            $values['time_stamp_ms'] = $row['time_stamp_ms'];
            $values['event_type'] = $row['event_type'];
            $values['trial_row_id'] = $row['trial_row_id'];

            if($row['type'] == 'event') {
                $fields = array('date_time', 'time_stamp_ms', 'trial_row_id',
                                'event_type', 'event_value',
                                'cue_pos_x', 'cue_pos_y', 'cue_vy', 'cue_target_offset');

                $query = 'INSERT INTO event_log (session_id, participant_id, ' .
                       join(', ', $fields) .
                       ') VALUES (:session_id, :participant_id, :' .
                       join(', :', $fields) .
                       ');';
                $handle = $link->prepare($query);

                if($row['event_type'] == 'dialog_response' ||
                   $row['event_type'] == 'speed_change' ||
                   $row['event_type'] == 'pause') {
                    $values['event_value'] = $row['event_value'];
                    $values['cue_pos_x'] = -1;
                    $values['cue_pos_y'] = -1;
                    $values['cue_vy'] = -1;
                    $values['cue_target_offset'] = -1;
                } else {
                    $cue = $row['event_value'];
                    $values['event_value'] = $cue['missed'] ? 0 : 1;
                    $values['cue_pos_x'] = $cue['value'];
                    $values['cue_pos_y'] = $cue['top'];
                    $values['cue_vy'] = $cue['velocity'];
                    $values['cue_target_offset'] = $cue['target_offset'];
                }
            } elseif ($row['type'] == 'input') {
                $fields = array('date_time', 'time_stamp_ms',
                                'event_type', 'event_value');

                $query = 'INSERT INTO input_log (session_id, participant_id, ' .
                       join(', ', $fields) .
                       ') VALUES (:session_id, :participant_id, :' .
                       join(', :', $fields) .
                       ');';
                $handle = $link->prepare($query);

                $values['event_value'] = $row['event_value'];
            }
            $handle->bindValue('session_id', $sessionID);
            $handle->bindValue('participant_id', $participantID);
            foreach($fields as $field) {
                $val = $values[$field];
                if(is_int($val)) {
                    $handle->bindValue($field, $val, PDO::PARAM_INT);
                } else {
                    $handle->bindValue($field, $val);
                }
            }
            $handle->execute();
        }
    }

    $link->commit();
    $result["success"] = true;
} catch (Exception $ex) {
    error_log("Database failed with message: " . $ex->getMessage() . "\n", 3, $error_log_file);
    #  echo "Exception on line ".$ex->getLine();
    $result['reason'] = $ex->getMessage();
    $result['current_row'] = $current_row;
}

print(json_encode($result))
    ?>

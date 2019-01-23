<?php

include '/PATH/TO/SECRETS/FILE.php';

$error_log_file = "/var/tmp/webdasher_errors.log";

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

    $handle = $link->prepare('INSERT INTO sessions (scenario, scenario_name, participant_id, start_time, browser_info, machine_info) VALUES (:scenario, :scenario_name, :participant_id, NOW(), :browser_info, :machine_info);');
    $handle->bindValue('scenario', $data['session']['scenario']);
    $handle->bindValue('scenario_name', $data['session']['scenario-name']);
    $handle->bindValue('participant_id', $data['session']['participant']);
    $handle->bindValue('browser_info', $data['session']['browser_info']);
    $handle->bindValue('machine_info', $data['session']['machine_info']);

    $handle->execute();

    $sessionID = $link->lastInsertId();
    $result['session-id'] = $sessionID;

    $link->commit();
    $result["success"] = true;
}
catch (Exception $ex) {
    error_log("Database failed with message: " . $ex->getMessage() . "\n", 3, $error_log_file);
    #  echo "Exception on line ".$ex->getLine();
    $result['reason'] = $ex->getMessage();
}

print(json_encode($result))
    ?>

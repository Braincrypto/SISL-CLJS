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

    if(isset($data['session'])) {
        $sessionID = $data['session'];
    }

    $handle = $link->prepare('SELECT id, finish_tag, TIMESTAMPDIFF(SECOND, finish_time, NOW()) as dt FROM sessions WHERE id = ?;');
    $handle->bindValue(1, $sessionID);
    $handle->execute();
    $session_exists = ($handle->rowCount() == 1);

    if ($session_exists) {
        $row = $handle->fetch();
        if(isset($row['finish_tag'])) {
            throw new Exception('Session already finished.');
        }
    } else {
        throw new Exception('No session found.');
    }

    $finish_tag = $sessionID . '-' . uniqid('', true);
    $handle = $link->prepare('UPDATE sessions SET finish_time = NOW(), finish_tag = :finish_tag WHERE id = :session_id;');
    $handle->bindValue('session_id', $sessionID, PDO::PARAM_INT);
    $handle->bindValue('finish_tag', $finish_tag);
    $handle->execute();

    $result['code'] = $finish_tag;

    $link->commit();
    $result["success"] = true;
}
catch (Exception $ex) {
    error_log("Database failed with message: " . $ex->getMessage(), 3, $error_log_file);
    #  echo "Exception on line ".$ex->getLine();
    $result['reason'] = $ex->getMessage();
}

print(json_encode($result))
    ?>

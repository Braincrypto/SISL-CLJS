function get_url_parameter(name)
{
  var regexS = "[\\?&]"+name+"=([^&#]*)";
  var regex = new RegExp( regexS );
  var tmpURL = window.location.href;
  var results = regex.exec( tmpURL );
  if( results == null )
    return null;
  else
    return results[1];
}

mode = ""

function modeSelect()
{
  var assignmentId = get_url_parameter('assignmentId');

  if (assignmentId == null) {
    mode_NonTurk();
  } else if (assignmentId == "ASSIGNMENT_ID_NOT_AVAILABLE") {
    mode_Preview();
  } else {
    mode_Active(assignmentId);
  }
}

function mode_NonTurk()
{
  mode = "NonTurk";
  document.getElementById('submitButton').disabled = true;
  document.getElementById('submitButton').value = "Welcome to our MTurk Experiment.";
  document.getElementById('completionCode').disabled = true;
}

function mode_Preview()
{
  mode = "Preview";
  document.getElementById('submitButton').disabled = true;
  document.getElementById('submitButton').value = "You must ACCEPT the HIT before you can submit the results.";
  document.getElementById('completionCode').disabled = true;
}

function mode_Active(assignmentId)
{
  mode = "Active";
  document.getElementById('submitButton').disabled = true;
  document.getElementById('submitButton').value = "You must COMPLETE the HIT before you can submit the results.";
  document.getElementById('completionCode').disabled = true;
  document.getElementById('assignmentId').value = assignmentId;
}

function mode_Finished(finishTag)
{
  if(mode != "Active") return;
  mode = "Finished";
  document.getElementById('submitButton').disabled = false;
  document.getElementById('submitButton').value = "Submit";
  document.getElementById('completionCode').disabled = false;
  document.getElementById('completionCode').value = finishTag;

  var form = document.getElementById('mturk_form');
  if (document.referrer && ( document.referrer.indexOf('workersandbox') != -1) ) {
    form.action = "https://workersandbox.mturk.com/mturk/externalSubmit";
  }
}

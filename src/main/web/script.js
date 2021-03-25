// set to "" if REST service and web page are hosted on the same server
const restBaseUrl = "";


$(document).ready(function() {

  setTranslationDirections();

  // add click event listener to translate button
  $("#transButton").click(translate);
})


/**
 * Query server for available translation directions and set them as options
 * at 'transDir' dropdown menu.
 */
function setTranslationDirections() {

  fetch(`${restBaseUrl}/getTranslationDirections`)
    .then(response => response.json())
    .then(transDirList => {
      let dropdown = $("#transDir");
      transDirList.forEach(
        transDir => dropdown.append(new Option(transDir.replace("-", " → "), transDir)));
    })
    .catch(errorMessage => console.log(errorMessage));
}


/**
 * Top level function to translate a document.
 */
async function translate() {

  if (!validate()) {
    return;
  }

  disableQuery();
  let jobId;

  postQueryForm()
    .then(async queryResponse => {
      jobId = await queryResponse.text();
      return pollTranslation(jobId, 5000, 10);
    })
    .then(async pollResponse =>
      saveTranslation(pollResponse))
    .finally(() => enableQuery())
    .catch(errorMessage => {
      alert(errorMessage);
      // in case of an error, request job deletion on server
      if (jobId) {
        fetch(`${restBaseUrl}/deleteTranslation/${jobId}`, {
            method: "DELETE"
          })
          .then(response => response.statusText)
          .then(statusText => console.log(`delete request result: ${statusText}`))
          .catch(deleteErrorMessage => console.log(`deletion failed: ${deleteErrorMessage}`));
      }
    });
}


/**
 * Check if all required fields in the query form are valid, i.e. filled.
 *
 * @return {boolean} true if all fields are valid, false otherwise
 */
function validate() {

  let allValid = true;
  if ($("#file").val() == "") {
    $("#file").addClass("query__param--invalid");
    allValid = false;
  }
  if (!$("#enc").val()) {
    $("#enc").addClass("query__param--invalid");
    allValid = false;
  }
  return allValid;
}


/**
 * Remove the css class marking the given form field as invalid.
 *
 * @param {Object} formField
 */
function removeInvalid(formField) {

  $(formField).removeClass("query__param--invalid");
}


/**
 * Post a translation query according to the filled query form.
 *
 * @return {Promise} promise representing the result of posting the query
 */
async function postQueryForm() {

  let queryResponse = await fetch(`${restBaseUrl}/translate`, {
    method: "POST",
    body: new FormData(queryForm)
  });

  // query parameter input fields can only be disabled *after* submitting
  $(".query__param").prop("disabled", true);

  if (!queryResponse.ok) {
    return Promise.reject(queryResponse.status + ": " + queryResponse.statusText);
  } else {
    return Promise.resolve(queryResponse);
  }
}


/**
 * Poll translation result for the given job.
 *
 * @param {string} jobId - the job identifier
 * @param {number} interval - milliseconds to wait between polls
 * @param {number} maxAttempts - maximum number of attempts
 * @return {Promise} promise holding the recursive function to execute the poll
 */
function pollTranslation(jobId, interval, maxAttempts) {

  console.log("start polling");
  let attempts = 0;

  const executePoll = async(resolve, reject) => {
    console.log("polling...");
    fetch(`${restBaseUrl}/getTranslation/${jobId}`)
      .then(pollResponse => {
        attempts++;
        if (pollResponse.status === 200) {
          return resolve(pollResponse);
        } else if (pollResponse.status === 404 || pollResponse.status === 500) {
          return reject(`Error: ${pollResponse.statusText}`);
        } else if (attempts === maxAttempts) {
          return reject("Server busy, please try again later.");
        } else {
          console.log(pollResponse.statusText);
          // go into recursion
          setTimeout(executePoll, interval, resolve, reject);
        }
      })
      .catch(errorMessage => {
        return reject(errorMessage);
      })
  };

  return new Promise(executePoll);
}


/**
 * Extract file name from the given poll response and save translation under that name.
 *
 * @param {Object} pollResponse
 */
async function saveTranslation(pollResponse) {

  // extract filename
  let fileName = decodeURIComponent(
    pollResponse.headers.get("content-disposition").split(";")[1].split("\"")[1]);

  // trigger save dialog
  let blob = await pollResponse.blob();
  let url = window.URL.createObjectURL(blob);
  let a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  // append element to dom, otherwise won't work in Firefox
  document.body.appendChild(a);
  a.click();
  // remove element again
  a.remove();
  window.URL.revokeObjectURL(url);
}


/**
 * Disable query form and translation button and turn on spinner.
 */
function disableQuery() {

  // query parameter input fields can only be disabled *after* submitting
  //$(".query-param").prop("disabled", true);
  $(".query__param").addClass("query__param--disabled");
  $(".query__param").removeClass("query__param--enabled");

  $(".translation__button").prop("disabled", true);
  $(".translation__button").prop("textContent", "Translating...");
  $(".translation__button").addClass("translation__button--disabled");
  $(".translation__button").removeClass("translation__button--enabled");

  // turn on spinner
  $(".spinner").toggleClass("spinner--enabled");
}


/**
 * Enable query form and translation button and turn off spinner.
 */
function enableQuery() {

  $(".query__param").prop("disabled", false);
  $(".query__param").removeClass("query__param--disabled");
  $(".query__param").addClass("query__param--enabled");

  $(".translation__button").prop("disabled", false);
  $(".translation__button").prop("textContent", "Translate");
  $(".translation__button").removeClass("translation__button--disabled");
  $(".translation__button").addClass("translation__button--enabled");

  // turn off spinner
  $(".spinner").toggleClass("spinner--enabled");
}
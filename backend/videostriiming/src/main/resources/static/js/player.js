var ws = new WebSocket('ws://' + location.host + '/player');
var video;
var webRtcPeer;
var state = null;
var isSeekable = false;

var I_CAN_START = 0;
var I_CAN_STOP = 1;
var I_AM_STARTING = 2;

let urlValue;

window.onload = function () {
    video = document.getElementById('video');
    setState(I_CAN_START);
    urlValue = document.getElementById('videourl').value;
    getStreams();
};

window.onbeforeunload = function () {
    ws.close();
};

ws.onmessage = function (message) {
    var parsedMessage = JSON.parse(message.data);
    console.info('Received message: ' + message.data);

    switch (parsedMessage.id) {
        case 'startResponse':
            startResponse(parsedMessage);
            break;
        case 'error':
            if (state === I_AM_STARTING) {
                setState(I_CAN_START);
            }
            onError('Error message from server: ' + parsedMessage.message);
            break;
        case 'playEnd':
            playEnd();
            break;
        case 'videoInfo':
            showVideoData(parsedMessage);
            break;
        case 'iceCandidate':
            webRtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                if (error)
                    return console.error('Error adding candidate: ' + error);
            });
            break;
        case 'seek':
            console.log(parsedMessage.message);
            break;
        case 'position':
            document.getElementById("videoPosition").value = parsedMessage.position;
            break;
        case 'iceCandidate':
            break;
        default:
            if (state == I_AM_STARTING) {
                setState(I_CAN_START);
            }
            onError('Unrecognized message', parsedMessage);
    }
}

function start() {
    // Disable start button
    setState(I_AM_STARTING);
    showSpinner(video);

    var mode = $('input[name="mode"]:checked').val();
    console.log('Creating WebRtcPeer in ' + mode + ' mode and generating local sdp offer ...');

    // Video and audio by default
    var userMediaConstraints = {
        audio: true,
        video: true
    }


    var options = {
        remoteVideo: video,
        mediaConstraints: userMediaConstraints,
        onicecandidate: onIceCandidate
    };

    console.info('User media constraints' + userMediaConstraints);

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
        function (error) {
            if (error)
                return console.error(error);
            webRtcPeer.generateOffer(onOffer);
        });
}

function onOffer(error, offerSdp) {
    if (error)
        return console.error('Error generating the offer');
    console.info('Invoking SDP offer callback function ' + location.host);

    const message = {
        id: 'start',
        sdpOffer: offerSdp,
        videourl: urlValue
    };
    sendMessage(message);
}

function onError(error) {
    console.error(error);
}

function onIceCandidate(candidate) {
    console.log('Local candidate' + JSON.stringify(candidate));

    const message = {
        id: 'onIceCandidate',
        candidate: candidate
    };
    sendMessage(message);
}

function startResponse(message) {
    setState(I_CAN_STOP);
    console.log('SDP answer received from server. Processing ...');

    webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
        if (error)
            return console.error(error);
    });
}

function pause() {
    togglePause()
    console.log('Pausing video ...');
    const message = {
        id: 'pause'
    }
    sendMessage(message);
}

function resume() {
    togglePause()
    console.log('Resuming video ...');
    const message = {
        id: 'resume'
    }
    sendMessage(message);
}

function stop() {
    console.log('Stopping video ...');
    setState(I_CAN_START);
    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;

        var message = {
            id: 'stop'
        }
        sendMessage(message);
    }
    hideSpinner(video);
}


function playEnd() {
    setState(I_CAN_START);
    hideSpinner(video);
}

function doSeek() {
    var message = {
        id: 'doSeek',
        position: document.getElementById("seekPosition").value
    }
    sendMessage(message);
}

function getPosition() {
    var message = {
        id: 'getPosition'
    }
    sendMessage(message);
}

function showVideoData(parsedMessage) {
    //Show video info
    isSeekable = parsedMessage.isSeekable;
    if (isSeekable) {
    } else {
        document.getElementById('isSeekable').value = "false";
    }

    document.getElementById('duration').value = parsedMessage.videoDuration;
}

function setState(nextState) {
    switch (nextState) {
        case I_CAN_START:
            enableButton('#start', 'start()');
            disableButton('#pause');
            disableButton('#stop');
            enableButton('#videourl');
            break;

        case I_CAN_STOP:
            disableButton('#start');
            enableButton('#pause', 'pause()');
            enableButton('#stop', 'stop()');
            disableButton('#videourl');
            break;

        case I_AM_STARTING:
            disableButton('#start');
            disableButton('#pause');
            disableButton('#stop');
            disableButton('#videourl');
            break;

        default:
            onError('Unknown state ' + nextState);
            return;
    }
    state = nextState;
}

function sendMessage(message) {
    const jsonMessage = JSON.stringify(message);
    console.log('Sending message: ' + jsonMessage);
    ws.send(jsonMessage);
}

function togglePause() {
    var pauseText = $("#pause-text").text();
    if (pauseText == " Resume ") {
        $("#pause-text").text(" Pause ");
        $("#pause-icon").attr('class', 'glyphicon glyphicon-pause');
        $("#pause").attr('onclick', "pause()");
    } else {
        $("#pause-text").text(" Resume ");
        $("#pause-icon").attr('class', 'glyphicon glyphicon-play');
        $("#pause").attr('onclick', "resume()");
    }
}

function disableButton(id) {
    $(id).attr('disabled', true);
    $(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
    $(id).attr('disabled', false);
    if (functionName) {
        $(id).attr('onclick', functionName);
    }
}

function showSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].poster = './img/transparent-1px.png';
        arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
    }
}

function hideSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].src = '';
        arguments[i].poster = './img/webrtc.png';
        arguments[i].style.background = '';
    }
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function (event) {
    event.preventDefault();
    $(this).ekkoLightbox();
});

function displayStreams(array) {
    for (let i = 0; i < array.length; i++) {
        const nodeContainer = document.createElement("DIV");
        let node = document.createElement('a');
        node.classList.add("py-1", "btn", "btn-link");
        node.innerText = array[i].date;
        const videoUrl = `file:///tmp/${array[i].id}.webm`
        node.setAttribute('tabindex', '0');
        node.addEventListener('click', function () {
            urlValue = videoUrl;
            document.getElementById('videourl').value = urlValue;
        });
        nodeContainer.appendChild(node);
        document.getElementById('stream-list').appendChild(nodeContainer);
    }
}

function getStreams() {
    fetch('http://localhost:8443/past-streams', {method: 'GET', headers: {'Content-Type': 'application/json'}}).then(
        response => response.json() .then(data => {
                console.log("STREAMS RESPONSE", data);
                displayStreams(data);
            }
        ))
}


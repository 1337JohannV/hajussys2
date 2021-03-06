var ws = new WebSocket('wss://' + '23.102.46.118:8443' + '/recording');
var video;
var webRtcPeer;

window.onload = function() {
    video = document.getElementById('video');
    disableStopButton();
};

window.onbeforeunload = function() {
    ws.close();
};

ws.onmessage = function(message) {
    const parsedMessage = JSON.parse(message.data);
    console.info('Received message: ' + message.data);

    switch (parsedMessage.id) {
        case 'viewerResponse':
            viewerResponse(parsedMessage);
            break;
        case 'iceCandidate':
            webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
                if (error)
                    return console.error('Error adding candidate: ' + error);
            });
            break;
        case 'stopCommunication':
            dispose();
            break;
        default:
            console.error('Unrecognized message', parsedMessage);
    }
};

function viewerResponse(message) {
    if (message.response !== 'accepted') {
        var errorMsg = message.message ? message.message : 'Unknown error';
        console.info('Call not accepted for the following reason: ' + errorMsg);
        dispose();
    } else {
        webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
            if (error)
                return console.error(error);
        });
    }
}

function viewer() {
    if (!webRtcPeer) {
        showSpinner(video);

        var options = {
            remoteVideo : video,
            onicecandidate : onIceCandidate
        }
        webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
            function(error) {
                if (error) {
                    return console.error(error);
                }
                this.generateOffer(onOfferViewer);
            });

        enableStopButton();
    }
}

function onOfferViewer(error, offerSdp) {
    if (error)
        console.log('error: ', error);
    console.info('Invoking SDP offer callback function ' + location.host);
    var message = {
        id : 'viewer',
        sdpOffer : offerSdp
    }
    sendMessage(message);
}

function onIceCandidate(candidate) {
    console.log("Local candidate" + JSON.stringify(candidate));

    var message = {
        id : 'onIceCandidate',
        candidate : candidate
    };
    sendMessage(message);
}

function stop() {
    var message = {
        id : 'stopView'
    }
    sendMessage(message);
    dispose();
}

function dispose() {
    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;
    }
    hideSpinner(video);

    disableStopButton();
}

function disableStopButton() {
    enableButton('#presenter', 'presenter()');
    enableButton('#viewer', 'viewer()');
    disableButton('#stop');
}

function enableStopButton() {
    disableButton('#presenter');
    disableButton('#viewer');
    enableButton('#stop', 'stop()');
}

function disableButton(id) {
    $(id).attr('disabled', true);
    $(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
    $(id).attr('disabled', false);
    $(id).attr('onclick', functionName);
}

function sendMessage(message) {
    var jsonMessage = JSON.stringify(message);
    console.log('Sending message: ' + jsonMessage);
    ws.send(jsonMessage);
}

function showSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].poster = './img/transparent-1px.png';
        arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
    }
}

function hideSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].src = '';
        arguments[i].poster = './img/webrtc.png';
        arguments[i].style.background = '';
    }
}

$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
    event.preventDefault();
    $(this).ekkoLightbox();
});

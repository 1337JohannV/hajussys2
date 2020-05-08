<template>
    <div class="container">
        <div class="page-header">
            <h1>Tutorial: WebRTC in loopback with recording</h1>
            <p>
                This application shows a <i>WebRtcEndpoint</i> connected to itself
                (loopback) where the media sent to server is recorded and then
                played. Then, the recorded media (stored in the KMS file system) is
                played again. Thus, in the server-side code of this application we
                are using two media pipelines <a
                    data-footer="The first pipeline performs a WebRTC loopback and the media is stored in the KMS file system. The second media pipeline is used to play the recorded media."
                    data-title="Hello World with Recording" data-toggle="lightbox"
                    href="img/pipelines.png">media
                pipelines</a> (one for the loopback, and the other one the playback).
                To run this demo follow these steps:
            </p>
            <ol>
                <li>Open this page with a browser compliant with WebRTC anc
                    click on <i>Start</i> button.
                <li>Grant the access to the camera and microphone. After the
                    SDP negotiation the loopback should start.
                <li>Click on <i>Stop</i> to finish the communication.
                <li>Click on <i>Play</i> to replay the recorded media.
                </li>
            </ol>
        </div>
        <div class="row">
            <div class="col-md-12">
                <input checked="checked" name="mode" type="radio"
                       value="video-and-audio"> Video and audio <input name="mode"
                                                                       type="radio" value="video-only"> Video only
                <input
                        name="mode" type="radio" value="audio-only"> Audio only
            </div>
        </div>
        <div class="row">
            <div class="row">
                <div class="col-md-5">
                    <h3>Local stream</h3>
                    <video autoplay height="360px" id="videoInput" poster="img/webrtc.png"
                           width="480px"></video>
                </div>
                <div class="col-md-2">
                    <a @click="start" class="btn btn-success" href="#"
                       id="start"><span
                            class="glyphicon glyphicon-play"></span> Start</a><br> <br> <a
                        @click="stop" class="btn btn-danger" href="#"
                        id="stop"><span
                        class="glyphicon glyphicon-stop"></span> Stop</a><br> <br> <a
                        @click="play" class="btn btn-warning" href="#"
                        id="play"><span
                        class="glyphicon glyphicon-play-circle"></span> Play</a>
                </div>
                <div class="col-md-5">
                    <h3>Remote stream</h3>
                    <video autoplay height="360px" id="videoOutput" poster="img/webrtc.png"
                           width="480px"></video>
                </div>
            </div>
            <div class="row">
                <div class="col-md-12">
                    <label class="control-label" for="console">Console</label><br>
                    <br>
                    <div class="democonsole" id="console">
                        <ul></ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import * as kurentoUtils from "kurento-utils";
    import $ from 'jquery'

    export default {
        name: 'HelloWorld',
        data: function () {
            return {
                ws: new WebSocket('wss://localhost:8443/recording'),
                videoInput: null,
                videoOutput: null,
                webRtcPeer: null,
                state: null,
                NO_CALL: 0,
                IN_CALL: 1,
                POST_CALL: 2,
                DISABLED: 3,
                IN_PLAY: 4
            }
        },
        mounted() {
            this.videoInput = document.getElementById('videoInput');
            this.videoOutput = document.getElementById('videoOutput');
            this.setState(this.NO_CALL);
        },

        props: {
            msg: String
        },
        methods: {
            start() {
                console.log('Starting video call ...');

                // Disable start button
                this.setState(this.DISABLED);
                console.log('Creating WebRtcPeer and generating local sdp offer ...');

                var options = {
                    localVideo: this.videoInput,
                    remoteVideo: this.videoOutput,
                    mediaConstraints: this.getConstraints(),
                    onicecandidate: this.onIceCandidate
                };

                this.webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
                    function (error) {
                        if (error)
                            return console.error(error);
                        this.webRtcPeer.generateOffer(this.onOffer);
                    });
            },

            onOffer(error, offerSdp) {
                if (error)
                    return console.error('Error generating the offer');
                console.info('Invoking SDP offer callback function ' + location.host);
                const message = {
                    id: 'start',
                    sdpOffer: offerSdp,
                    mode: $('input[name="mode"]:checked').val()
                };
                this.sendMessage(message);
            },

            onError(error) {
                console.error(error);
            },

            onIceCandidate(candidate) {
                console.log('Local candidate' + JSON.stringify(candidate));

                const message = {
                    id: 'onIceCandidate',
                    candidate: candidate
                };
                this.sendMessage(message);
            },

            startResponse(message) {
                this.setState(this.IN_CALL);
                console.log('SDP answer received from server. Processing ...');

                this.webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
                    if (error)
                        return console.error(error);
                });
            },

            stop() {
                const stopMessageId = (this.state === this.IN_CALL) ? 'stop' : 'stopPlay';
                console.log('Stopping video while in ' + this.state + '...');
                this.setState(this.POST_CALL);
                if (this.webRtcPeer) {
                    this.webRtcPeer.dispose();
                    this.webRtcPeer = null;

                    const message = {
                        id: stopMessageId
                    };
                    this.sendMessage(message);
                }
            },

            play() {
                console.log("Starting to play recorded video...");

                // Disable start button
                this.setState(this.DISABLED);
                console.log('Creating WebRtcPeer and generating local sdp offer ...');

                const options = {
                    remoteVideo: this.videoOutput,
                    mediaConstraints: this.getConstraints(),
                    onicecandidate: this.onIceCandidate
                };

                this.webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
                    function (error) {
                        if (error)
                            return console.error(error);
                        this.webRtcPeer.generateOffer(this.onPlayOffer);
                    });
            },

            onPlayOffer(error, offerSdp) {
                if (error)
                    return console.error('Error generating the offer');
                console.info('Invoking SDP offer callback function ' + location.host);
                const message = {
                    id: 'play',
                    sdpOffer: offerSdp
                };
                this.sendMessage(message);
            },

            getConstraints() {
                const mode = $('input[name="mode"]:checked').val();
                const constraints = {
                    audio: true,
                    video: true
                };

                if (mode === 'video-only') {
                    constraints.audio = false;
                } else if (mode === 'audio-only') {
                    constraints.video = false;
                }

                return constraints;
            },

            playResponse(message) {
                this.setState(this.IN_PLAY);
                this.webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
                    if (error)
                        return console.error(error);
                });
            },

            playEnd() {
                this.setState(this.POST_CALL);
            },

            sendMessage(message) {
                const jsonMessage = JSON.stringify(message);
                console.log('Sending message: ' + jsonMessage);
                this.ws.send(jsonMessage);
            },
            setState(nextState) {
                switch (nextState) {
                    case this.NO_CALL:
                        $('#start').attr('disabled', false);
                        $('#stop').attr('disabled', true);
                        $('#play').attr('disabled', true);
                        break;
                    case this.DISABLED:
                        $('#start').attr('disabled', true);
                        $('#stop').attr('disabled', true);
                        $('#play').attr('disabled', true);
                        break;
                    case this.IN_CALL:
                        $('#start').attr('disabled', true);
                        $('#stop').attr('disabled', false);
                        $('#play').attr('disabled', true);
                        break;
                    case this.POST_CALL:
                        $('#start').attr('disabled', false);
                        $('#stop').attr('disabled', true);
                        $('#play').attr('disabled', false);
                        break;
                    case this.IN_PLAY:
                        $('#start').attr('disabled', true);
                        $('#stop').attr('disabled', false);
                        $('#play').attr('disabled', true);
                        break;
                    default:
                        this.onError('Unknown state ' + nextState);
                        return;
                }
                this.state = nextState;
            }
        }
    }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
    h3 {
        margin: 40px 0 0;
    }

    ul {
        list-style-type: none;
        padding: 0;
    }

    li {
        display: inline-block;
        margin: 0 10px;
    }

    a {
        color: #42b983;
    }
</style>

<template>
    <div class="row m-0">
        <div class="col">
            <div class="row justify-content-center">
                <div class="col-4 text-center">
                    <h5>Select Streaming Type</h5>
                </div>
            </div>
            <div class="row">
                <div class="col">
                    <div class="btn-group w-100" role="group">
                        <button 
                            class="btn" 
                            :class="{'btn-success': selectedType.videoAudio, 'btn-outline-dark': !selectedType.videoAudio}"
                            v-on:click="changeStreamingType(0)"
                        >
                            Video and audio
                        </button>
                        <button 
                            class="btn" 
                            :class="{'btn-success': selectedType.video, 'btn-outline-dark': !selectedType.video}"
                            v-on:click="changeStreamingType(1)"
                        >
                            Video only
                        </button>
                        <button 
                            class="btn" 
                            :class="{'btn-success': selectedType.audio, 'btn-outline-dark': !selectedType.audio}"
                            v-on:click="changeStreamingType(2)"
                        >
                            Audio only
                        </button>
                    </div>
                </div>
            </div>
            <div class="row mt-4">
                <div class="col-5 text-center">
                    <h3>Local stream</h3>
                    <video 
                        autoplay 
                        height="360px" 
                        id="videoInput"
                        class="video-canvas"
                        width="480px">
                    </video>
                </div>
                <div class="col-2 text-center">
                    <div class="row">
                        <div class="col m-2">
                            <button 
                                @click="start" 
                                class="btn btn-success stream-button" 
                                id="start"
                                :disabled="buttonDisabled.start"
                                >
                                Start
                            </button>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col m-2">
                            <button
                                @click="stop" 
                                class="btn btn-danger stream-button" 
                                id="stop"
                                :disabled="buttonDisabled.stop"
                                >
                                Stop
                            </button>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col m-2">
                            <button
                                @click="play" 
                                class="btn btn-warning stream-button" 
                                id="play"
                                :disabled="buttonDisabled.play"
                                >
                                Play
                            </button>
                        </div>
                    </div>
                </div>
                <div class="col-5 text-center">
                    <h3>Remote stream</h3>
                    <video 
                        autoplay 
                        height="360px" 
                        id="videoOutput"
                        class="video-canvas"
                        width="480px">
                    </video>
                </div>
            </div>
            <div class="row">
                <div class="col-12">
                    <h5 
                        class="text-center" 
                        for="console">
                        Console
                    </h5>
                    <div 
                        class="democonsole p-4" 
                        id="console">
                        <ul></ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import * as kurentoUtils from "kurento-utils";
    import constants from "../utils/constants";
    import $ from 'jquery'

    export default {
        name: 'stream',
        data: function () {
            return {
                selectedType: {
                    videoAudio: true,
                    video: false,
                    audio: false
                },
                buttonDisabled: {
                    start: false,
                    stop: false,
                    play: false
                },
                ws: new WebSocket('ws://localhost:8443/recording'),
                videoInput: document.getElementById('videoInput'),
                videoOutput: document.getElementById('videoOutput'),
                webRtcPeer: null,
                state: null
            }
        },
        props: {
            msg: String
        },
        methods: {
            changeStreamingType(type){
                this.selectedType.videoAudio = false;
                this.selectedType.video = false;
                this.selectedType.audio = false;
                switch(type){
                    case(0):
                        this.selectedType.videoAudio = true;
                        break;
                    case(1):
                        this.selectedType.video = true;
                        break;
                    case(2):
                        this.selectedType.audio = true;
                        break;
                }
            },
            start() {
                console.log('Starting video call ...');

                // Disable start button
                this.setState(constants.DISABLED);
                console.log('Creating WebRtcPeer and generating local sdp offer ...');

                var options = {
                    localVideo: this.videoInput,
                    remoteVideo: this.videoOutput,
                    mediaConstraints: this.getConstraints(),
                    onicecandidate: this.onIceCandidate
                };

                this.webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
                    function (error) {
                        console.log(error, 'fk')
                        if (error) {
                            console.log(error, 'fk')
                        }
                    });
                this.webRtcPeer.generateOffer(this.onOffer);
                console.log(this.webRtcPeer,'peer')
            },

            onOffer(error, offerSdp) {
                if (error)
                    return console.error('Error generating the offer');
                console.info('Invoking SDP offer callback function ' + location.host);
                const message = {
                    id: 'start',
                    sdpOffer: offerSdp,
                    mode: 'video-only'
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
                this.setState(constants.IN_CALL);
                console.log('SDP answer received from server. Processing ...');

                this.webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
                    if (error)
                        console.log('start err')
                        return console.error(error);
                });
            },

            stop() {
                const stopMessageId = (this.state === constants.IN_CALL) ? 'stop' : 'stopPlay';
                console.log('Stopping video while in ' + this.state + '...');
                this.setState(constants.POST_CALL);
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
                this.setState(constants.DISABLED);
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
                this.setState(constants.IN_PLAY);
                this.webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
                    if (error)
                        return console.error(error);
                });
            },

            playEnd() {
                this.setState(constants.POST_CALL);
            },

            sendMessage(message) {
                const jsonMessage = JSON.stringify(message);
                console.log('Sending message: ' + jsonMessage);
                this.ws.send(jsonMessage);
            },
            setState(nextState) {
                switch (nextState) {
                    case constants.NO_CALL:
                        this.buttonDisabled.start = false;
                        this.buttonDisabled.stop = true;
                        this.buttonDisabled.play = true;
                        break;
                    case constants.DISABLED:
                        this.buttonDisabled.start = true;
                        this.buttonDisabled.stop = true;
                        this.buttonDisabled.play = true;
                        break;
                    case constants.IN_CALL:
                        this.buttonDisabled.start = true;
                        this.buttonDisabled.stop = false;
                        this.buttonDisabled.play = true;
                        break;
                    case constants.POST_CALL:
                        this.buttonDisabled.start = false;
                        this.buttonDisabled.stop = true;
                        this.buttonDisabled.play = false;
                        break;
                    case constants.IN_PLAY:
                        this.buttonDisabled.start = true;
                        this.buttonDisabled.stop = false;
                        this.buttonDisabled.play = true;
                        break;
                    default:
                        this.onError('Unknown state ' + nextState);
                        return;
                }
                this.state = nextState;
            }
        },
        mounted() {
            this.setState(constants.NO_CALL);
            this.ws.onmessage =  (message) => {
                let parsedMessage = JSON.parse(message.data);
                console.info('Received message: ' + message.data);
                switch (parsedMessage.id) {
                    case 'startResponse':
                        this.startResponse(parsedMessage);
                        break;
                    case 'playResponse':
                        this.playResponse(parsedMessage);
                        break;
                    case 'playEnd':
                        this.playEnd();
                        break;
                    case 'error':
                        this.setState(constants.NO_CALL);
                        this.onError('Error message from server: ' + parsedMessage.message);
                        break;
                    case 'iceCandidate':
                        this.webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
                            if (error)
                                return console.error('Error adding candidate: ' + error);
                        });
                        break;
                    default:
                        this.setState(constants.NO_CALL);
                        this.onError('Unrecognized message', parsedMessage);
                }
            }
        },
    }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
    .stream-button {
        min-width: 5rem;
    }
    .video-canvas {
        border: 1px solid black;
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

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
                                id="start">
                                Start
                            </button>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col m-2">
                            <button
                                @click="stop" 
                                class="btn btn-danger stream-button" 
                                id="stop">
                                Stop
                            </button>
                        </div>
                    </div>
                    <div class="row">
                        <div class="col m-2">
                            <button
                                @click="play" 
                                class="btn btn-warning stream-button" 
                                id="play">
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
                        class="control-label text-center" 
                        for="console">
                        Console
                    </h5>
                    <br>
                    <br>
                    <div 
                        class="democonsole" 
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
    import $ from 'jquery'

    export default {
        name: 'HelloWorld',
        data: function () {
            return {
                selectedType: {
                    videoAudio: true,
                    video: false,
                    audio: false
                },
                ws: new WebSocket('ws://localhost:8443/recording'),
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
                        this.setState(this.NO_CALL);
                        this.onError('Error message from server: ' + parsedMessage.message);
                        break;
                    case 'iceCandidate':
                        this.webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
                            if (error)
                                return console.error('Error adding candidate: ' + error);
                        });
                        break;
                    default:
                        this.setState(this.NO_CALL);
                        this.onError('Unrecognized message', parsedMessage);
                }
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
                this.setState(this.IN_CALL);
                console.log('SDP answer received from server. Processing ...');

                this.webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
                    if (error)
                        console.log('start err')
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

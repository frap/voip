(ns voip.core.audio
  (:require [manifold.stream :as s]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [clojure.core.async :refer [chan go <! >!]])
  (:import (javax.sound.sampled
            AudioFormat
            DataLine$Info
            TargetDataLine
            AudioSystem
            AudioInputStream
            SourceDataLine
            Port Port$Info)))


(def audio-format
  (let [sample-rate (float 8000.0)
        sample-size 16
        channels 1
        signed true
        big-endian true]
    (AudioFormat. sample-rate sample-size channels signed big-endian)))

; supported mixers
(def mixer-info (seq (. AudioSystem (getMixerInfo))))

; get mixer-info, name, description of each mixer
(def mixer-info-list
  (map #(let [m %] { :mixer-info m
                     :name (. m (getName))
                     :description (. m (getDescription))}) mixer-info))
(defn mixers
  "Returns a list of all available mixers."
  []
  (map #(AudioSystem/getMixer %)
       (AudioSystem/getMixerInfo)))

(defn capture
  "Create a voice capture stream. Optionally allows you to specify the buffer size."
  ([] (capture 2 10))

  ([frame-size buffer-size]
   (let [stream (s/stream buffer-size)
         continue (atom true)]

     (manifold.deferred/future
       (let [line-info (DataLine$Info. TargetDataLine audio-format)
             line (AudioSystem/getLine line-info)]

         (doto line
           (.open)
           (.start))

         (try
           (while @continue
             (let [buffer (byte-array frame-size)
                   read-count (.read line buffer 0 (alength buffer))]
               (when (> read-count 0)
                 (do (println (str "reading from mic: " buffer)) 
                   (s/put! stream buffer))
                 )))

           (catch Exception e (println e))

           (finally
             (s/close! stream)))))

     {:stop   #(reset! continue false)
      :stream stream})))

(defn play-streams
  "Takes a stream of streams and mixes them"
  ([streams] (play-streams streams 100000))

  ([streams frame-size]
   (let [continue (atom true)]
     (future

       (let [line-info (DataLine$Info.  SourceDataLine audio-format)
             line (AudioSystem/getLine line-info)]

         (doto line
           (.open)
           (.start))

         (s/consume
           (fn [stream]
             (let [buffer (byte-array frame-size)
                   input-stream (bs/to-input-stream stream)
                   audio-stream (AudioInputStream. input-stream audio-format AudioSystem/NOT_SPECIFIED)]

               (try
                 (while @continue
                   ;read from the audio stream into our buffer
                   (let [read-count (.read audio-stream buffer 0 (alength buffer))]
                     ;check if we have reached the end of stream
                     (if (= read-count -1)
                       (reset! continue false)
                       (when (> read-count 1)
                         ;we actually read something, so play on speakers
                         (.write line buffer 0 read-count)))))

                 (catch Exception e (println e)))))

           streams)

         (doto line
           (.drain)
           (.close))))


     ;return a function to stop playing
     #(reset! continue false))))


(defn play-stream
  "Play a manifold stream of bytes (allows you to specify a frame size)
    this should be no smaller then the the frame-size specified in capture"

  ([stream]
   (play-stream stream 100000))

  ([stream frame-size]
   (let [continue (atom true)]

     (future
       (let [buffer (byte-array frame-size)
             input-stream (bs/to-input-stream stream)
             audio-stream (AudioInputStream. input-stream audio-format AudioSystem/NOT_SPECIFIED)
             line-info (DataLine$Info. SourceDataLine audio-format)
             line (AudioSystem/getLine line-info)]

         (doto line
           (.open)
           (.start))

         (try
           (while @continue
             ;read from the audio stream into our buffer
             (let [read-count (.read audio-stream buffer 0 (alength buffer))]
               ;check if we have reached the end of stream
               (if (= read-count -1)
                 (reset! continue false)
                 (when (> read-count 1)
                   ;we actually read something, so play on speakers
                   (.write line buffer 0 read-count)))))

           (catch Exception e (println e))

           (finally
             (doto line
               (.drain)
               (.close))))))


     ;return a function to stop playing
     #(reset! continue false))))


(defn play-streams2
  [streams]
  (let [stoppers (atom '())
        continue (atom true)]
    (s/consume
      (fn [stream]
        (when @continue
          (let [stop (play-stream stream 1000)]
            (swap! stoppers #(conj % stop)))))
      streams)
    #(do
      (swap! continue (constantly false))
      (doseq [stop @stoppers]
        (stop)))))

(defn capture-test []
  (let [frame-size 2
        {stream :stream stop :stop} (capture frame-size 0)]
    (play-stream stream frame-size)
    (Thread/sleep 20000)
    (stop)))

(defn bad-test [dropped]
  (let [frame-size 2
        {stream :stream stop :stop} (capture frame-size 0)
        derived (s/filter (fn [_] (> (rand-int 100) dropped)) stream)]
    (play-stream derived frame-size)
    (Thread/sleep 10000)
    (stop)))

(defn record-test []
  (let [{stream :stream stop :stop} (capture 2 10000)
        cached (s/stream->seq stream)
        second (s/->source cached)
        third (s/->source cached)]
    (Thread/sleep 3000)
    (stop)

    (play-stream second)
    (Thread/sleep 3000)
    (play-stream third)))

; create a desired pcm audio format
; -> float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian
(def wavformat (new AudioFormat 44100 16 1 false true))
(def buffer-size (* 22050 2))   ; 44k = 1/2 sec x 2 bytes / sample mono


(comment
;; from each mixer-info, get the target data lines (inputs)
;(def input-lines
;  (mapcat #(let [mix-info (:mixer-info %)
;                 mixer    (. AudioSystem (getMixer mix-info))
;                 targets  (. mixer (getTargetLineInfo))]
;              (map #(let [target-info     %
;                          get-line    (fn[]
;                                      (let [mixer (. AudioSystem (getMixer mix-info))
;                                            line  (. mixer (getLine target-info))]
;                                      (do (. line (open wavformat buffer-size))
;                                          (. line (start))
;                                          (sync nil
;                                            ; ### should close old input - When @input ...
;                                            (set input (. mixer (getLine target-info)))
;                                            (. @input (start))))))
;                          mixer-name  (. mix-info (getName))]
;                      {:mixer-name  mixer-name
;                       :get-line    get-line}) targets)) mixer-info-list))

; get a set of samples for level-checking in ui
; assume we are dealing with signed 16 bit samples (short = 2 x bytes)
; may want to sync on 'input'
(defn get-inst-samples ([num-samples]
  (do
    (if (not (. @input (isOpen)))
      (. @input (open wavformat buffer-size)))
    (if (not (. @input (isRunning)))
      (. @input (start)))
    (let [bytes   (make-array (. Byte TYPE) (* 2 num-samples))
          bcount  (. @input (read bytes 0 (* 2 num-samples)))
          bbyte   (. ByteBuffer (wrap bytes))
          bshort  (. bbyte (asShortBuffer))
          shorts  (make-array (. Short TYPE) num-samples)
          ]
      (do (print "Read: " (str bcount) " bytes.")
          (newline)
          (. *out* (flush))
          (. bshort (get shorts 0 num-samples))
          shorts)))))
; "Built-in Microphone"  <-- we'll use this

; get the mixer info for the mic
(def mic-mixer-info
  (:mixer-info (first (filter #(= "Built-in Microphone" (:name %)) mixer-info-list))))

; get the built in mic mixer
(def mic (. AudioSystem (getMixer mic-mixer-info)))

; get the supported source and target lines for the mixer
(def sources (seq (. mic (getSourceLineInfo))))   ; nil
(def targets (seq (. mic (getTargetLineInfo))))   ; (interface TargetDataLine supporting 72 audio formats)

; get a target line
(def line-info (first targets))
(def mic-line (. mic (getLine line-info)))

; add a line listener for events on the line
(. mic-line (addLineListener
              (implement [LineListener]
                (update [evt]
                  (do (print "Event: " (. evt (getType)))
                      (newline)
                      (. *out* (flush)))))))

; check if we can get this format from the built in mic
(. mic-line (open format buffer-size))

; start the input
(. mic-line (start))

; try looping and counting available samples
(dotimes i 100
  (print "Available data: " (. mic-line (available)))
  (newline)
  (. *out* (flush))
  (let [buffer  (make-array (. Byte TYPE) 2048)
        bcount  (. mic-line (read buffer 0 2048))
        bbyte   (. ByteBuffer (wrap buffer))
        bshort  (. bbyte (asShortBuffer))
        ]
    (print "Read: " bcount " bytes. Buffer state:" (str bshort))
    (print " ... Converted to short: "  (str (. bshort (get 0))))
    (newline))
  (. Thread (sleep 20)))   ; 1 milli sleep = 1/1000 of a sec = 44 samples

; stop the input
;(. mic-line (stop))

; close mic
;(. mic-line (close))

)

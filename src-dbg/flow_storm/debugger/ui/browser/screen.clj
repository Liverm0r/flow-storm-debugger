(ns flow-storm.debugger.ui.browser.screen
  (:require [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler v-box h-box label button add-class list-view]]
            [flow-storm.utils :refer [log-error]]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.target-commands :as target-commands]
            [clojure.string :as str])
  (:import [javafx.scene.control CheckBox SplitPane]
           [javafx.scene Node]
           [javafx.geometry Orientation Pos]
           [javafx.scene.input MouseButton]))

(defn make-inst-var [var-ns var-name]
  {:inst-type :var
   :var-ns var-ns
   :var-name var-name})

(defn make-inst-ns
  ([ns-name] (make-inst-ns ns-name nil))
  ([ns-name profile]
   {:inst-type :ns
    :profile profile
    :ns-name ns-name}))

(defn add-to-var-instrumented-list [var-ns var-name]
  (let [[{:keys [add-all]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (add-all [(make-inst-var var-ns var-name)])))

(defn remove-from-var-instrumented-list [var-ns var-name]
  (let [[{:keys [remove-all]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (remove-all [(make-inst-var var-ns var-name)])))

(defn- instrument-function [var-ns var-name add?]

  (target-commands/run-command :instrument-fn {:fn-symb (symbol var-ns var-name)})

  (when add?
    (add-to-var-instrumented-list var-ns var-name)))

(defn- uninstrument-function [var-ns var-name remove?]

  (target-commands/run-command :uninstrument-fns {:vars-symbs [(symbol var-ns var-name)]})

  (when remove?
    (remove-from-var-instrumented-list var-ns var-name)))

(defn add-to-namespace-instrumented-list [ns-maps]
  (let [[{:keys [add-all]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (add-all ns-maps)))

(defn remove-from-namespace-instrumented-list [ns-maps]
  (let [[{:keys [remove-all]}] (obj-lookup "browser-observable-instrumentations-list-data")]
    (remove-all ns-maps)))

(defn- instrument-namespaces [inst-namespaces add?]
  (target-commands/run-command
   :instrument-namespaces
   {:ns-names (map :ns-name inst-namespaces)
    :profile (:profile (first inst-namespaces))})

  (when add?
    (add-to-namespace-instrumented-list inst-namespaces)))

(defn- uninstrument-namespaces [inst-namespaces remove?]
  (target-commands/run-command
   :uninstrument-namespaces
   {:ns-names (map :ns-name inst-namespaces)})

  (when remove?
    (remove-from-namespace-instrumented-list inst-namespaces)))

(defn- update-selected-fn-detail-pane [{:keys [added ns name file static line arglists doc]}]
  (let [[browser-instrument-button]   (obj-lookup "browser-instrument-button")
        [selected-fn-fq-name-label]   (obj-lookup "browser-selected-fn-fq-name-label")
        [selected-fn-added-label]     (obj-lookup "browser-selected-fn-added-label")
        [selected-fn-file-label]      (obj-lookup "browser-selected-fn-file-label")
        [selected-fn-static-label]    (obj-lookup "browser-selected-fn-static-label")
        [selected-fn-args-list-v-box] (obj-lookup "browser-selected-fn-args-list-v-box")
        [selected-fn-doc-label]       (obj-lookup "browser-selected-fn-doc-label")

        args-lists-labels (map (fn [al] (label (str al))) arglists)]

    (add-class browser-instrument-button "enable")
    (.setOnAction browser-instrument-button (event-handler [_] (instrument-function ns name true)))
    (.setText selected-fn-fq-name-label (format "%s" #_ns name))
    (when added
      (.setText selected-fn-added-label (format "Added: %s" added)))
    (.setText selected-fn-file-label (format "File: %s:%d" file line))
    (when static
      (.setText selected-fn-static-label "Static: true"))
    (-> selected-fn-args-list-v-box .getChildren .clear)
    (.addAll (.getChildren selected-fn-args-list-v-box)
             (into-array Node args-lists-labels))
    (.setText selected-fn-doc-label doc)))

(defn- update-vars-pane [vars]
  (let [[{:keys [clear add-all]}] (obj-lookup "browser-observable-vars-list-data")]
    (clear)
    (add-all (sort-by :var-name vars))))

(defn- update-namespaces-pane [namespaces]
  (let [[{:keys [clear add-all]}] (obj-lookup "browser-observable-namespaces-list-data")]
    (clear)
    (add-all (sort namespaces))))

(defn get-var-meta [{:keys [var-name var-ns]}]
  (target-commands/run-command :get-var-meta
                               {:var-ns var-ns :var-name var-name}
                               (fn [var-meta]
                                 (ui-utils/run-later (update-selected-fn-detail-pane var-meta)))))

(defn- get-all-vars-for-ns [ns-name]
  (target-commands/run-command :get-all-vars-for-ns
                               {:ns-name ns-name}
                               (fn [all-vars]
                                 (ui-utils/run-later (update-vars-pane (->> all-vars
                                                                            (map #(assoc % :inst-type :var))))))))

(defn get-all-namespaces []
  (target-commands/run-command :get-all-namespaces
                               {}
                               (fn [all-namespaces]
                                 (ui-utils/run-later (update-namespaces-pane all-namespaces)))))

(defn create-namespaces-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell ns-name]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label ns-name)))
                    :on-click (fn [mev sel-items {:keys [list-view-pane]}]
                                (cond (= MouseButton/PRIMARY (.getButton mev))
                                      (let [sel-cnt (count sel-items)]
                                        (when (= 1 sel-cnt)
                                          (get-all-vars-for-ns (first sel-items))))

                                      (= MouseButton/SECONDARY (.getButton mev))
                                      (let [ctx-menu-instrument-ns-light {:text "Instrument namespace :light"
                                                                          :on-click (fn []
                                                                                      (instrument-namespaces (map #(make-inst-ns % :light) sel-items) true))}
                                            ctx-menu-instrument-ns-full {:text "Instrument namespace :full"
                                                                         :on-click (fn []
                                                                                     (instrument-namespaces (map #(make-inst-ns % :full) sel-items) true))}
                                            ctx-menu (ui-utils/make-context-menu [ctx-menu-instrument-ns-light
                                                                                  ctx-menu-instrument-ns-full])]

                                        (.show ctx-menu
                                               list-view-pane
                                               (.getScreenX mev)
                                               (.getScreenY mev)))))
                    :selection-mode :multiple
                    :search-predicate (fn [ns-name search-str]
                                        (str/includes? ns-name search-str))})]

    (store-obj "browser-observable-namespaces-list-data" lv-data)

    list-view-pane))

(defn create-vars-pane []
  (let [{:keys [list-view-pane] :as lv-data}
        (list-view {:editable? false
                    :cell-factory-fn (fn [list-cell {:keys [var-name]}]
                                       (.setText list-cell nil)
                                       (.setGraphic list-cell (label var-name)))
                    :on-click (fn [mev sel-items _]
                                (cond (= MouseButton/PRIMARY (.getButton mev))
                                      (let [sel-cnt (count sel-items)]
                                        (when (= 1 sel-cnt)
                                          (get-var-meta (first sel-items))))))
                    :selection-mode :single
                    :search-predicate (fn [{:keys [var-name]} search-str]
                                        (str/includes? var-name search-str))})]

    (store-obj "browser-observable-vars-list-data" lv-data)
    list-view-pane))

(defn create-fn-details-pane []
  (let [selected-fn-fq-name-label (label "" "browser-fn-fq-name")
        inst-button (button "Instrument" "browser-instrument-btn")
        name-box (doto (h-box [selected-fn-fq-name-label inst-button])
                   (.setAlignment Pos/CENTER_LEFT))
        selected-fn-added-label (label "" "browser-fn-attr")
        selected-fn-file-label (label "" "browser-fn-attr")
        selected-fn-static-label (label "" "browser-fn-attr")
        selected-fn-args-list-v-box (v-box [] "browser-fn-args-box")
        selected-fn-doc-label (label "" "browser-fn-attr")

        selected-fn-detail-pane (v-box [name-box selected-fn-args-list-v-box
                                        selected-fn-added-label selected-fn-doc-label
                                        selected-fn-file-label selected-fn-static-label])]

    (store-obj "browser-instrument-button" inst-button)
    (store-obj "browser-selected-fn-fq-name-label" selected-fn-fq-name-label)
    (store-obj "browser-selected-fn-added-label" selected-fn-added-label)
    (store-obj "browser-selected-fn-file-label" selected-fn-file-label)
    (store-obj "browser-selected-fn-static-label" selected-fn-static-label)
    (store-obj "browser-selected-fn-args-list-v-box" selected-fn-args-list-v-box)
    (store-obj "browser-selected-fn-doc-label" selected-fn-doc-label)

    selected-fn-detail-pane))

(defn- instrumentations-cell-factory [list-cell {:keys [inst-type] :as inst}]
  (try
    (let [inst-box (case inst-type
                     :var (let [{:keys [var-name var-ns]} inst
                                inst-lbl (doto (h-box [(label "VAR:" "browser-instr-type-lbl")
                                                       (label (format "%s/%s" var-ns var-name) "browser-instr-label")])
                                           (.setSpacing 10))
                                inst-del-btn (doto (button "del" "browser-instr-del-btn")
                                               (.setOnAction (event-handler
                                                              [_]
                                                              (uninstrument-function var-ns var-name true))))]
                            (doto (h-box [inst-lbl inst-del-btn])
                              (.setSpacing 10)
                              (.setAlignment Pos/CENTER_LEFT)))
                     :ns (let [{:keys [ns-name] :as inst-ns} inst
                               inst-lbl (doto (h-box [(label "NS:" "browser-instr-type-lbl")
                                                      (label ns-name "browser-instr-label")])
                                          (.setSpacing 10))
                               inst-del-btn (doto (button "del" "browser-instr-del-btn")
                                              (.setOnAction (event-handler
                                                             [_]
                                                             (uninstrument-namespaces [inst-ns] true))))]
                           (doto (h-box [inst-lbl inst-del-btn])
                             (.setSpacing 10)
                             (.setAlignment Pos/CENTER_LEFT))))]
      (.setGraphic ^Node list-cell inst-box))
    (catch Exception e (log-error e))))

(defn- create-instrumentations-pane []
  (let [{:keys [list-view-pane get-all-items] :as lv-data} (list-view {:editable? false
                                                                      :selection-mode :single
                                                                      :cell-factory-fn instrumentations-cell-factory})
        delete-all-btn (doto (button "Delete all")
                         (.setOnAction (event-handler
                                        [_]
                                        (let [type-groups (group-by :inst-type (get-all-items))
                                              del-namespaces   (:ns type-groups)
                                              del-vars (:var type-groups)]

                                          (uninstrument-namespaces del-namespaces true)

                                          (doseq [v del-vars]
                                            (uninstrument-function (:var-ns v) (:var-name v) true))))))
        en-dis-chk (doto (CheckBox.)
                     (.setSelected true))
        _ (.setOnAction en-dis-chk
                        (event-handler
                         [_]
                         (let [type-groups (group-by :inst-type (get-all-items))
                               change-namespaces   (:ns type-groups)
                               change-vars (:var type-groups)]

                           (if (.isSelected en-dis-chk)
                             (instrument-namespaces change-namespaces false)
                             (uninstrument-namespaces change-namespaces false))

                           (doseq [v change-vars]
                             (if (.isSelected en-dis-chk)
                               (instrument-function (:var-ns v) (:var-name v) false)
                               (uninstrument-function (:var-ns v) (:var-name v) false))))))
        instrumentations-tools (doto (h-box [(label "Enable all")
                                             en-dis-chk
                                             delete-all-btn]
                                            "browser-instr-tools-box")
                                 (.setSpacing 10)
                                 (.setAlignment Pos/CENTER_LEFT))
        pane (v-box [(label "Instrumentations")
                     instrumentations-tools
                     list-view-pane])]

    (store-obj "browser-observable-instrumentations-list-data" lv-data)

    pane))

(defn main-pane []
  (let [namespaces-pane (create-namespaces-pane)
        vars-pane (create-vars-pane)
        selected-fn-detail-pane (create-fn-details-pane)
        inst-pane (create-instrumentations-pane)
        top-split-pane (doto (SplitPane.)
                         (.setOrientation (Orientation/HORIZONTAL)))
        top-bottom-split-pane (doto (SplitPane.)
                                (.setOrientation (Orientation/VERTICAL)))]

    (-> top-split-pane
        .getItems
        (.addAll [namespaces-pane vars-pane selected-fn-detail-pane]))

    (-> top-bottom-split-pane
        .getItems
        (.addAll [top-split-pane
                  inst-pane]))

    (.setDividerPosition top-split-pane 0 0.3)
    (.setDividerPosition top-split-pane 1 0.6)

    (.setDividerPosition top-bottom-split-pane 0 0.7)

    top-bottom-split-pane
    ))

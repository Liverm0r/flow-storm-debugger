(ns flow-storm.debugger.ui.flows.screen
  (:require [flow-storm.debugger.ui.flows.code :as flow-code]
            [flow-storm.debugger.ui.flows.call-tree :as flow-tree]
            [flow-storm.debugger.ui.flows.functions :as flow-fns]
            [flow-storm.debugger.ui.state-vars :refer [store-obj obj-lookup] :as ui-vars]
            [flow-storm.debugger.ui.utils :as ui-utils :refer [event-handler run-now v-box h-box label icon tab-pane tab]]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.target-commands :as target-commands]))

(defn remove-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        [flow-tab] (obj-lookup flow-id "flow_tab")]

    (when flow-tab
      ;; remove the tab from flows_tabs_pane
      (-> flows-tabs-pane
          .getTabs
          (.remove flow-tab)))

    ;; clean ui state vars
    (ui-vars/clean-flow-objs flow-id)))

(defn create-empty-flow [flow-id]
  (let [[flows-tabs-pane] (obj-lookup "flows_tabs_pane")
        threads-tab-pane (tab-pane {:closing-policy :all-tabs
                                    :drag-policy :reorder})
        flow-tab (if (= flow-id dbg-state/orphans-flow-id)
                         (tab {:graphic (icon "mdi-filter") :content threads-tab-pane})
                         (tab {:text (str "flow-" flow-id) :content threads-tab-pane}))]

    (.setOnCloseRequest flow-tab
                        (event-handler
                         [ev]
                         (dbg-state/remove-flow flow-id)
                         (remove-flow flow-id)
                         ;; since we are destroying this tab, we don't need
                         ;; this event to propagate anymore
                         (.consume ev)))

    (store-obj flow-id "threads_tabs_pane" threads-tab-pane)
    (store-obj flow-id "flow_tab" flow-tab)
    (-> flows-tabs-pane
        .getTabs
        (.addAll [flow-tab]))))

(defn- create-thread-controls-pane [flow-id thread-id]
  (let [first-btn (doto (ui-utils/icon-button "mdi-page-first")
                   (.setOnAction (event-handler [ev] (flow-code/jump-to-coord flow-id thread-id 0))))
        prev-btn (doto (ui-utils/icon-button "mdi-chevron-left")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                 thread-id
                                                 (dec (dbg-state/current-idx flow-id thread-id))))))
        curr-trace-lbl (label "1")
        separator-lbl (label "/")
        thread-trace-count-lbl (label "?")
        _ (store-obj flow-id (ui-vars/thread-curr-trace-lbl-id thread-id) curr-trace-lbl)
        _ (store-obj flow-id (ui-vars/thread-trace-count-lbl-id thread-id) thread-trace-count-lbl)
        {:keys [flow/execution-expr]} (dbg-state/get-flow flow-id)
        execution-expression? (and (:ns execution-expr)
                                   (:form execution-expr))
        next-btn (doto (ui-utils/icon-button "mdi-chevron-right")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                 thread-id
                                                 (inc (dbg-state/current-idx flow-id thread-id))))))
        last-btn (doto (ui-utils/icon-button "mdi-page-last")
                   (.setOnAction (event-handler
                                  [ev]
                                  (flow-code/jump-to-coord flow-id
                                                           thread-id
                                                           (dec (dbg-state/thread-trace-count flow-id thread-id))))))
        re-run-flow-btn (doto (ui-utils/icon-button "mdi-cached")
                          (.setOnAction (event-handler
                                         [_]
                                         (when execution-expression?
                                           (target-commands/run-command :re-run-flow {:flow-id flow-id :execution-expr execution-expr}))))
                          (.setDisable (not execution-expression?)))
        trace-pos-box (doto (h-box [curr-trace-lbl separator-lbl thread-trace-count-lbl] "trace-position-box")
                        (.setSpacing 2.0))
        controls-box (doto (h-box [first-btn prev-btn re-run-flow-btn next-btn last-btn])
                       (.setSpacing 2.0))]

    (doto (h-box [controls-box trace-pos-box] "thread-controls-pane")
      (.setSpacing 2.0))))

(defn- create-thread-pane [flow-id thread-id]
  (let [thread-controls-pane (create-thread-controls-pane flow-id thread-id)
        code-tab (tab {:graphic (icon "mdi-code-parentheses")
                       :content (flow-code/create-code-pane flow-id thread-id)})

        callstack-tree-tab (tab {:graphic (icon "mdi-file-tree")
                                 :content (flow-tree/create-call-stack-tree-pane flow-id thread-id)
                                 :on-selection-changed (event-handler [_] (flow-tree/update-call-stack-tree-pane flow-id thread-id))})

        instrument-tab (tab {:graphic (icon "mdi-format-list-numbers")
                             :content (flow-fns/create-functions-pane flow-id thread-id)
                             :on-selection-changed (event-handler [_] (flow-fns/update-functions-pane flow-id thread-id))})
        thread-tools-tab-pane (tab-pane {:tabs [code-tab callstack-tree-tab instrument-tab]
                                         :side :bottom
                                         :closing-policy :unavailable})
        thread-pane (v-box [thread-controls-pane thread-tools-tab-pane])]

    (store-obj flow-id (ui-vars/thread-tool-tab-pane-id thread-id) thread-tools-tab-pane)

    ;; make thread-tools-tab-pane take the full height
    (-> thread-tools-tab-pane
        .prefHeightProperty
        (.bind (.heightProperty thread-pane)))

    thread-pane))

(defn create-empty-thread [flow-id thread-id]
  (run-now
   (let [[threads-tabs-pane] (obj-lookup flow-id "threads_tabs_pane")
         thread-tab-pane (create-thread-pane flow-id thread-id)
         thread-tab (tab {:text (str "thread-" thread-id)
                          :content thread-tab-pane})]
     (-> threads-tabs-pane
         .getTabs
         (.addAll [thread-tab])))))

(defn main-pane []
  (let [t-pane (tab-pane {:closing-policy :all-tabs
                          :side :top})]
    (store-obj "flows_tabs_pane" t-pane)
    t-pane))

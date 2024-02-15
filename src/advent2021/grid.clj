(ns advent2021.grid
  (:require [clojure.data.priority-map :refer [priority-map]]))

(defn parse [lines sq-f]
  (->> lines
       (map seq)
       (map #(mapv sq-f %))
       (vec)))

(defn bounds [grid]
  [(count (first grid))
   (count grid)])

(defn out-of-bounds? [grid [x y]]
  (or (< y 0)
      (>= y (count grid))
      (< x 0) (>= x (count (first grid)))))

(defn neighbors [grid [x y]]
  (remove
   nil?
   (for [[dx dy] [[-1 0] [1 0] [0 -1] [0 1]]]
     (let [[nx ny] [(+ x dx) (+ y dy)]]
       (if (out-of-bounds? grid [nx ny])
         nil
         [nx ny])))))

(defn coords [grid]
  (let [xmax (count (first grid))
        ymax (count grid)]
    (for [x (range 0 xmax)
          y (range 0 ymax)]
      [x y])))


;; we search for the end.
;; cost to get to the end is just our risk.
;; I am going to extract this to a common function because I'm tired of
;; programming A* search :-)
(defn a*-search [start goal neighbors heuristic distance]
  (loop [[goal-score open-set nodes]
         [{start 0}
         (priority-map start 0)
          0]]
    (if-let [[current _] (peek open-set)]
      (cond
        (= current goal) (goal-score current)
        (> nodes 1000000) (throw (Exception. "too many nodes"))
        :else (recur
        (reduce
         (fn [[goal-score open-set nodes] neighbor]
           (let [n-distance (distance current neighbor)
                 n-new-score (+ (goal-score current) n-distance)
                 n-score (get goal-score neighbor Integer/MAX_VALUE)]
             (if (< n-new-score n-score)
               [(assoc goal-score neighbor n-new-score)
               (assoc open-set neighbor (+ n-new-score (heuristic neighbor)))
                nodes]
               [goal-score open-set nodes])))
         [goal-score (pop open-set) (inc nodes)]
         (neighbors current))))
      (throw (Exception. "could not reach goal")))))

(ns advent2021.day22
  (:require [advent2021.utils :as utils]
            [clojure.math.combinatorics :as combo]
            [clojure.core.match :refer [match]]))

;; part 1 can be brute forced (1 million cubes) but part 2
;; almost certainly can't be.
;; I guess it is time to make a bunch of cubes and split them apart when they
;; hit each other &c.

(def line-re #"(on|off) x=(-?\d+)\.\.(-?\d+),y=(-?\d+)\.\.(-?\d+),z=(-?\d+)\.\.(-?\d+)")

(defn parse-line [line]
  (->> line
       (re-matches line-re)
       (rest)
       (map-indexed (fn [n x] (if (= n 0) x (utils/parse-int x))))
       ((fn [[mode xstart xend ystart yend zstart zend]]
          {:mode mode
           :coords [[xstart xend] [ystart yend] [zstart zend]]}))))


(defn vertices [{:keys [coords]}]
  (let [[[xstart xend] [ystart yend] [zstart zend]] coords]
    (for [x [xstart xend]
          y [ystart yend]
          z [zstart zend]]
      [x y z])))

(defn edges [cuboid]
  (->> (combo/combinations (vertices cuboid) 2)
       (filter (fn [[[x1 y1 z1] [x2 y2 z2]]]
                 (>=
                  (+
                  (if (= x1 x2) 1 0)
                  (if (= y1 y2) 1 0)
                  (if (= z1 z2) 1 0))
                  2)))))

(defn faces [cuboid]
  ;; a face is just all the vertices that are part of it.
  (->> (combo/combinations (vertices cuboid) 4)
      (filter (fn [vlist]
                (or (apply = (map #(nth % 0) vlist))
                    (apply = (map #(nth % 1) vlist))
                    (apply = (map #(nth % 2) vlist)))))))

;; (defn edge-intersects-face2? [edge vlist]
;;   (let [[v1 v2] edge
;;         [x2 y2 z2] v2
;;         [f1 f2 f3 f4] vlist
;;         [fx1 fy1 fz1] f1
;;         [fx2 fy2 fz2] f2
;;         [fx3 fy3 fz3] f3
;;         [fx4 fy4 fz4] f4]
;;   (match v1
;;     [x1 y2 z2] (println "x1 varied")
;;     [x2 y1 z2] (println "y1 varied")
;;     [x2 y2 z1] (println "z1 varied"))))

;; (edge-intersects-face2? [[-10 11 11] [15 11 11]] [[10 10 10] [10 10 12] [10 12 10] [10 12 12]])

(defn edge-intersects-face? [edge vlist]
  ;; to see if an edge intersects the face, we have, for
  ;; the edge; some axis that varies, and two that don't
  ;; basically - does the varying thing have a point
  ;; inside the face?
  ;; can do it brute force way - x varies, check the y/z are inside face y z
  ;; bounds &c.
  ;; let's try to be a little smarter.
  ;; I am not certain that this smarter way has ended up clearer.
  (let [edge-vary-idx (->> (map = (first edge) (second edge))
                           (map-indexed vector)
                           (remove (partial second))
                           (map first)
                           (first))
        other-idxs (remove (partial = edge-vary-idx) [0 1 2])
        face-idxs (->> other-idxs
                       (map (fn [n] (map #(nth % n) vlist))))
        coords-for-vary (map #(nth % edge-vary-idx) vlist)]
    (if (apply = coords-for-vary)
      (let [min-face (map #(reduce min %) face-idxs)
            max-face (map #(reduce max %) face-idxs)
            edge-non-vary-coords (map #(nth (first edge) %) other-idxs)
            edge-coords (map #(nth % edge-vary-idx) edge)
            face-vary-coord (first coords-for-vary)]
        (and
         (every? true? (map <= min-face edge-non-vary-coords max-face))
         ;; this ensures that the edge "breaks" the face vs just touches it.
         (< (first edge-coords) face-vary-coord (second edge-coords))
         ))
      false)))

(assert (edge-intersects-face? [[-10 11 11] [15 11 11]] [[10 10 10] [10 10 12] [10 12 10] [10 12 12]]))
(assert (not (edge-intersects-face? [[15 11 11] [25 11 11]] [[10 10 10] [10 10 12] [10 12 10] [10 12 12]])))

(def test-cuboid (parse-line (first (utils/read-input "day22-example.txt"))))
(def test-cuboid2 (parse-line (second (utils/read-input "day22-example.txt"))))
(vertices test-cuboid)
(edges test-cuboid)
(faces test-cuboid)

;; this does not test for total containment which is its own
;; method (relatively easy - all vertices of one cuboid inside
;; the other cuboid).
(defn cuboids-intersection [cuboid1 cuboid2]
  (->> (for [edge (edges cuboid1)
                  face (faces cuboid2)]
         (if (edge-intersects-face? edge face)
           [edge face]
           nil))
            (remove nil?)))

(cuboids-intersection test-cuboid test-cuboid2)

(defn point-within? [{:keys [coords]} [x y z]]
  (let [[[xstart xend] [ystart yend] [zstart zend]] coords]
    (and (<= xstart x xend)
         (<= ystart y yend)
         (<= zstart z zend))))

;; okay, so, if an edge intersects a face it needs to break the cuboid into
;; parts
;; implementing this is going to break me, it seems too complicated.

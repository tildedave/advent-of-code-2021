(ns advent2021.day19
  (:require [clojure.math.combinatorics :as combo]
            [advent2021.utils :as utils]
            [clojure.set :as set]
            [clojure.math :as math]))

;; so we need a bunch of rotation matrices.
;;     (1 0 0)
;; I = (0 1 0)
;;     (0 0 1)
;; essentially any rotation matrix that respects handedness
;; (e.g. does not flip) and has no real eigenvectors is
;; what we're looking for.
;; the space should be generated by 3 matrices and their inverses.
;; also rotating 3 in one direction is the same as rotating backwards,
;; that sort of thing.
;; so the general rotation (keeping z fixed) is
;;     (cos th -sin th 0)
;; R = (sin th cos th  0)
;;     (0      0       1)
;; except here theta is only pi / 2, pi, 2pi
;; and in fact we only need 1 rotation per axis as the others
;; are generated by it.
;; here are our rotation matrices:
;;   (0 -1 0)   (0 0 -1)    (1 0 0)
;; z (1 0 0)  y (0 1 0)   x (0 0 -1)
;;   (0 0 1)    (1 0 0)     (0 1 0)
;; then I think it is just I, -I.
;; OK, these 3 and their additive inverses generate the space of rotations.
;; so, then it is a matter of matching all the points.

(def rotation-x [[1 0 0] [0 0 -1] [0 1 0]])
(def rotation-y [[0 0 -1] [0 1 0] [1 0 0]])
(def rotation-z [[0 -1 0] [1 0 0] [0 0 1]])

(defn transform [rotation-matrix v]
  [(reduce + (map * (rotation-matrix 0) v))
   (reduce + (map * (rotation-matrix 1) v))
   (reduce + (map * (rotation-matrix 2) v))])

(defn minus [rotation-matrix]
  (mapv #(mapv (partial -) %) rotation-matrix))

(defn transpose [[row1 row2 row3]]
  [(mapv #(% 0) [row1 row2 row3])
   (mapv #(% 1) [row1 row2 row3])
   (mapv #(% 2) [row1 row2 row3])])

(defn mult [matrix1 matrix2]
  (let [[row1 row2 row3] (transpose matrix2)]
    [(transform matrix1 row1)
     (transform matrix1 row2)
     (transform matrix1 row3)]))

(mult rotation-x rotation-x)

(def all-rotations
  (->> [rotation-x rotation-y rotation-z]
       (mapcat #(vector % (mult % %) (mult (mult % %) %) (mult (mult % %) (mult % %))))
       (mapcat #(vector % (minus %)))))

;; so we also have the challenge of translations.

;; manhattan distance between the points is invariant after rotation.
;; I mean, any distance metric is invariant after rotation if it's a real
;; rotation :-)
;; mahattan distance should be invariant after translation too.
;; so the manhattan distance is the way to go.

(defn manhattan-distance [v1 v2]
  (->> (map - v1 v2)
       (map abs)
       (reduce +)))

(defn euclidean-distance-squared [v1 v2]
  (->> (map - v1 v2)
       (map #(* % %))
       (reduce +)))

(def pts-1
  [[-1,-1,1]
   [-2,-2,2]
   [-3,-3,3]
   [-2,-3,1]
   [5,6,-4]
   [8,0,7]])

(def pts-2  [[1,-1,1]
             [2,-2,2]
             [3,-3,3]
             [2,-1,3]
             [-5,4,-6]
             [-8,-7,0]])

;; convert points into set
;; transform points (all matrices)
;; after transformation, we need to see if what translation is
(defn apply-translation [v1 v2]
  (mapv + v1 v2))

(defn vector-between [v1 v2]
  (mapv - v1 v2))

;; however, the distance does not solve everything by itself.
;; we also the challenge that only 12 points will overlap.
;; so distance between each set of points.
;; the example and the input both have 25-26 beacons each.
;; I think in practice this will be kind of obvious.

;; compute mahattan distance of each scanner (can be used for all detections)

(defn parse-point [line]
  (->> (.split line ",")
       (mapv utils/parse-int)))


(defn parse-report [lines]
  (->> lines
       (remove #(.contains % "scanner"))
       (partition-by (partial = ""))
       (remove (partial = (list "")))
       (map #(map parse-point %))
       (map-indexed vector)
       (into {})))

(def example-report (parse-report (utils/read-input "day19-example.txt")))
(def input-report (parse-report (utils/read-input "day19.txt")))

(->> (example-report 1)
     (#(combo/combinations % 2))
     (map #(vector % (apply euclidean-distance-squared %)))
     (into {}))

(defn distance-map [points]
  (->> points
       (#(combo/combinations % 2))
       (map #(vector % (apply euclidean-distance-squared %)))
       (into {})))

(defn unchoose-2
  "n = (k 2) -> find k."
  [n]
  (let [ans (* n 2)]
    (loop [i 1]
      (let [test (* i (dec i))]
        (cond
          (= test ans) i
          (> test ans) -1
          :else (recur (inc i)))))))

(defn cross-product
  "compute the cross product of two vectors"
  [[a1 a2 a3] [b1 b2 b3]]
  [(- (* a2 b3) (* a3 b2))
   (- (* a3 b1) (* a1 b3))
   (- (* a1 b2) (* a2 b1))])

(defn normal-plane [p1 p2 p3]
  (cross-product (vector-between p2 p1) (vector-between p3 p1)))

(assert
 (= [3 9 1] (normal-plane [-1 1 2] [-4 2 2] [-2 1 5])))

(defn points-from-distance-map [common-distances inv-map]
  (->> common-distances
       (mapcat inv-map)
       (map #(assoc {} % 1))
       (apply merge-with +)
           ;; TODO: perhaps some filtering here by count,
           ;; as we saw some odd behavior on the input.
       ))

(defn get-dist [d p1 p2]
  (if (contains? d [p1 p2]) (d [p1 p2]) (d [p2 p1])))

;; we need 3 unique points from both to form a plane.
;; then we want to validate the other common points.
(defn find-rotation-and-translation [report scanner1 scanner2]
  (let [d1 (distance-map (report scanner1))
        d2 (distance-map (report scanner2))
        inv-d1 (set/map-invert d1)
        inv-d2 (set/map-invert d2)
        common-distances (set/intersection (set (keys inv-d1)) (set (keys inv-d2)))]
    (if (<= (unchoose-2 (count common-distances)) 1)
      nil
      (let [;;   points-n1 (points-from-distance-map common-distances inv-d1)
        ;;   points-n2 (points-from-distance-map common-distances inv-d1)
          ;; we need 3 points from n1 and 3 point that are the same from n2,
          ;; then we rotate one until we find the same normal vector,
          ;; then we figure out the translation from that.
          ;; not my comfort zone but it will be fun to see it work :-)
            d (nth (seq common-distances) 1)
            _ (println d)
            [p1 p2] (inv-d1 d)
            [p1' p2'] (inv-d2 d)
            _ (println p1 p2 p1' p2')
            ;; we now need to find a point so that p1 -> p3 is in the common
            ;; distances.
            ;; the ordering is annoying.
            p3 (->> d1 (keys)
                    (filter (fn [[p q]]
                              (or (and (= p1 p) (contains? inv-d2 (get-dist d1 p1 q)))
                                  (and (= p1 q) (contains? inv-d2 (get-dist d1 p1 p))))))
                    (map (fn [[p q]] (if (= p1 p) q p)))
                    (first))
            ;; we find p3, this is arbitrary.
            [p1' p2' p3'] (-> (get-dist d1 p1 p3) (inv-d2)
                              (conj p1' p2')
                              (set)
                              (seq))
            ;; these might not be the in the right order, however.
            ;; we need dist(p1,p2) = dist(p1',p2') etc.
            ;; so we will choose all permutations of these,
            ;; and take the ones that behave like we want.
            [p1' p2' p3'] (->>
                           (for [[p1' p2' p3'] (combo/permutations [p1' p2' p3'])]
                             (if
                              (and (= (get-dist d2 p1' p2') (get-dist d1 p1 p2))
                                   (= (get-dist d2 p1' p3') (get-dist d1 p1 p3))
                                   (= (get-dist d2 p2' p3') (get-dist d1 p2 p3)))
                               [p1' p2' p3']
                               nil))
                           (remove nil?)
                           (first))
            _ (println  "p1' p2' p3'" p1' p2' p3')
            n1 (normal-plane p1 p2 p3)
            n2 (normal-plane p1' p2' p3')
            rotation (->> all-rotations
                          (map #(vector % (transform % n2)))
                          (filter #(= (second %) n1))
                          (map first)
                          (first))
            translation (vector-between p1 (transform rotation p1'))]
            (println "rotation" rotation)
            (println "translation" translation)
            (println "p1 p2 p3" [p1 p2 p3])
            (println p1 (apply-translation (transform rotation p1') translation))
            (println p2 (apply-translation (transform rotation p2') translation))
            (println p3 (apply-translation (transform rotation p3') translation))
      ))))
        ;;   ;; now we have the rotation.  to get the translation we choose
        ;;   ;; essentially any point, rotate it via the matrix, see the
        ;;   ;; translation of its corresponding point.
        ;;   ;; unfortunately, we still have the problem that we don't really
        ;;   ;; know which point is p1 and which point is p2, etc.
        ;;   ;; so without this we do not know.
        ;;     ]
        ;; (for [[p1' p2' p3'] (combo/permutations [p1' p2' p3'])]
        ;;   ;; our mapping is p1 -> p1' p2 -> p2' p3 -> p3'
        ;;   ;; we want to know if, after we rotate these points,
        ;;   ;; there is a consistent translation.
        ;; ;;   (do (println p1' p2' p3')
        ;;   (->> [p1' p2' p3']
        ;;        (map #(transform rotation %))
        ;;        (map vector-between points)))

        ;;  ))))

        ;;   [p1' p2' p3'] (-> (get-dist d1 p1 p3)
        ;;                     (inv-d2)
        ;;                     (conj p1')
        ;;                     (conj p2')
        ;;         '            (set))]
    ;;   (println [p1 p2 p3] [p1' p2' p3'])
    ;;   (println (get-dist d1 p1 p3) (get-dist d1 p3 p2) (get-dist d1 p1 p2))
    ;;   (println (get-dist d2 p3' p1') (get-dist d2 p2' p3') (get-dist d2 p1' p2'))
    ;;   ))))
  ;; take 3 points from n1, form a normal vector from them, rotate until we
  ;; find the orientation of *the same* 3 points from n2.
  ;; so the previous approach had this.

(find-rotation-and-translation example-report 0 1)

    ;;   (let [[d1 d2] (take 2 common-distances)
    ;;         [p1 p2] (inv-d1 d1)
    ;;         [p1' p2'] (inv-d2 d1)
    ;;         ;; so the issue is that the points are not really aligned, and so
    ;;         ;; the vector between them really makes no sense.
    ;;         [p3 p4] (inv-d1 d2)
    ;;         [p3' p4'] (inv-d2 d2)
    ;;         _ (println [p1 p2] [p1' p2'])
    ;;         _ (println [p3 p4] [p3' p4'])
    ;;         dest-vect (vector-between p1 p2)
    ;;         dest-vect2 (vector-between p3 p4)
    ;;         _ (println  "dest vect (p1 p2)" dest-vect)
    ;;         _ (println  "dest vect (p1' p2')" (vector-between p1' p2'))
    ;;         _ (println  "dest vect (p3 p4)" dest-vect2)
    ;;         _ (println  "dest vect (p3' p4')" (vector-between p3' p4'))
    ;;         rotations (->> all-rotations
    ;;                        (map #(vector % (vector-between (transform % p1') (transform % p2'))))
    ;;                        (filter #(= dest-vect (second %)))
    ;;                        (map first))
    ;;         _ (println (->> all-rotations
    ;;                         (map #(vector % (vector-between (transform % p1') (transform % p2'))))
    ;;                         (filter #(= dest-vect (second %)))
    ;;                         (map first)))
    ;;         _ (println (->> all-rotations
    ;;                         (map #(vector % (vector-between (transform % p3') (transform % p4'))))
    ;;                         (filter #(= dest-vect2 (second %)))
    ;;                         (map first)))
    ;;         rotation (first rotations)
    ;;         translation (vector-between p1 (transform rotation p1'))]
    ;;     (assert (= p1 (apply-translation translation (transform rotation p1'))))
    ;;     (assert (= p2 (apply-translation translation (transform rotation p2'))))
    ;;     (assert (= p3 (apply-translation translation (transform rotation p3'))))
    ;;     (assert (= p4 (apply-translation translation (transform rotation p4'))))
    ;;     [rotation translation]))))

        ;; (println rotation translation)
        ;; (println "some sanity checks now")
        ;; (println
        ;; (println (apply-translation translation (transform rotation  p2')) "vs" p2)
        ;; [rotation translation]
        ;; ;; so there should be only one valid rotation
        ;; (println "valid rotations" rotations)))))



    ;;         transf-seq (map #(vector-between (transform % p1) (transform % p2)) all-rotations)
    ;;         ]
    ;;     (println "find a rotation so that we can transform" d1 "for " p1 p2 p1' p2')
    ;;     (println "looking for " dest-vect)
    ;;     (println (contains? (set transf-seq) dest-vect))
    ;;   ))))
        ;; for each rotation, apply to p1' p2' / p3' p4', see if this creates a
        ;; consistent translation to get back to p1 p2 p3 p4.
        ;; we need two
        ;; (map #(transform % p1) all-rotations)

(find-rotation-and-translation example-report 0 1)
(map #(count (find-rotation-and-translation input-report 0 %)) (range 38))


;; 12 choose 2 is 66 - so more than 66 is our combo.
;; inclusion-exclusion principle should carry us home without actually
;; mapping the beacons.
;; we can of course map the beacons too.

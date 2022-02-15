package models;

import java.util.Objects;

public class BucketCode {
    int leftPointBucket;
    int rightPointBucket;

    public BucketCode(int left, int right){
        leftPointBucket = left;
        rightPointBucket = right;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof BucketCode )){
            return false;
        }
        BucketCode code = (BucketCode) o;
        return (leftPointBucket == code.leftPointBucket && rightPointBucket == code.rightPointBucket) || (leftPointBucket == code.rightPointBucket && rightPointBucket == code.leftPointBucket);
    }

    @Override
    public int hashCode() {
        if (leftPointBucket > rightPointBucket) {
            return Objects.hash(leftPointBucket, rightPointBucket);
        } else {
            return Objects.hash(rightPointBucket, leftPointBucket);
        }
    }
}

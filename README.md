# N5 In-memory Stream from Github
N5 library implementation using Github backend.

In-memory stream read of N5 from Github repo without the need to clone

### How to use:
		N5Reader reader = new N5GithubReader(URL,BRANCH_NAME);
		CachedCellImg<FloatType, ?> img = N5Utils.open(reader, DATASET);
		ImageJFunctions.show(img);
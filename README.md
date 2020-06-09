# `@atomist/cljformat-skill`

<!---atomist-skill-readme:start--->

Activate the Clojure/ClojureScript formatting tool [cljfmt][cljfmt] so that it
runs on all Pushes to GitHub.   
  
# What it's useful for

There are already great ways to integrate [cljfmt][cljfmt] into your local development flow.  See the docs on
[editor support here][editor-support].  This skill activates the tool for those Commits that slip through, or 
for users that do not have cljfmt integrated into the local developer flows.

This can be integrated without changing how you do CI, and without making any changes to existing projects.  It is
designed to add automatic clojure formatting to any project running on GitHub.

# Before you get started

Connect and configure these integrations:

1. **GitHub**

The **GitHub** integration must be configured in order to use this skill. 
At least one repository must be selected. 

# How to configure

1.  **Choose which branches should be automatically formatted**

    Users can choose between 3 options:
    1. `update default branch` - run `cljfmt fix` only for the default branch.  Push changes directly to master.
    2. `update in a PR` - run on all branch ref pushes.  Changes are pushed via Pull Request.
    3. `update all branches` - reformats all branches
        
2.  **Select some Repos**

    Either select all, if all your Repositories should participate, or choose a subset of Repositories that should 
    stay formatted.  This skill will take no action on repositories that do not contain `.clj`, `.cljs`, or `cljc` files.
    
3.  **Default Formatting rules**

    [cljfmt configuration documentation][configuration] outlines different ways that users can control how the code
    is formatted.  A single shared set of rules (in the form of a GitHub gist), can be configured to drive this skill.
    Any repo that contains a `cljfmt.edn` in the root will use these rules instead.  If using a gist url,
    the gist must be either public, or must be stored in a GitHub organization authorized with the GitHub application.

# How to Use

If users are using standard formatting conventions (or if `cljfmt` is running as part of their local dev workflow),
then this skill will mostly do nothing!  However, if there are lots of unformatted Commits arrived at GitHub, then this
skill will make Commits, and raise Pull Requests with helpful formatting.  In practice, these Commits tend to get less
and less frequent as teams adopt to their internal formatting conventions.

Code reviews are so much better when your team is using the same formatting.  And a huge thanks to [cljfmt](cljfmt)!!!

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack)
[cljfmt]: https://github.com/weavejester/cljfmt
[editor-support]: https://github.com/weavejester/cljfmt#editor-support
[configuration]: https://github.com/weavejester/cljfmt#configuration
